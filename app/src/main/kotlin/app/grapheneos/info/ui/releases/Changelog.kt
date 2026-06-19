package app.grapheneos.info.ui.releases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.grapheneos.info.ui.reusablecomposables.ClickableText
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

// Single XML parser factory reused for every changelog entry, hardened against XXE / entity-expansion
// DoS in remote feed content.
private val changelogDocumentBuilderFactory: DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { isExpandEntityReferences = false }
        runCatching { isXIncludeAware = false }
    }

private val whitespaceRegex = Regex("\\s+")

// Resolve a feed href to a safe URL
// Site-relative ("/...") and anchor ("#...") links map to
// mosaicos.io; everything else must already be https. Non-https/unknown schemes return null so the
// text is rendered without a launchable link
private fun sanitizeFeedUrl(href: String?): String? {
    if (href == null) return null
    val home = "https://mosaicos.io"
    val url = when {
        href.startsWith('/') -> "$home$href"
        href.startsWith('#') -> "$home/releases$href"
        else -> href
    }
    return if (url.startsWith("https://", ignoreCase = true)) url else null
}

// Render model.
// The entry XML is parsed into a flat list of these blocks in plain (non-composable) code inside
// remember(...), with every AnnotatedString (text, links, bold/italic spans, "URL" annotations) fully
// precomputed. The composables below only render the finished model. Building AnnotatedStrings as a
// side effect of @Composable traversal was non-deterministic when an item was composed during the
// initial auto-scroll, which could leave headings blank; precomputing removes that entirely.

private sealed interface ChangelogBlock {
    val text: AnnotatedString

    data class Title(override val text: AnnotatedString) : ChangelogBlock
    data class Paragraph(override val text: AnnotatedString, val likelyHeading: Boolean) : ChangelogBlock
    data class Heading(override val text: AnnotatedString, val level: Int, val likelyHeading: Boolean) : ChangelogBlock
    data class ListEntry(override val text: AnnotatedString, val marker: String, val depth: Int) : ChangelogBlock
}

private fun normalizedNodeName(n: Node?): String =
    (n?.localName ?: n?.nodeName ?: "").lowercase()

private fun isLikelyHeading(text: AnnotatedString): Boolean =
    text.text == "Tags:" || text.text.startsWith("Changes since the")

// Parse one entry into its render model, or null if the (remote, possibly hostile) XML can't be parsed.
private fun parseChangelog(entry: String, linkColor: Color): List<ChangelogBlock>? = runCatching {
    val document = changelogDocumentBuilderFactory.newDocumentBuilder()
        .parse(InputSource(StringReader("<entry>$entry</entry>")))
        .also { it.documentElement.normalize() }
    val blocks = mutableListOf<ChangelogBlock>()
    collectBlocks(document.documentElement, linkColor, blocks)
    blocks
}.getOrNull()

private fun collectBlocks(node: Node, linkColor: Color, out: MutableList<ChangelogBlock>) {
    val children = node.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child.nodeType != Node.ELEMENT_NODE) continue

        when (val name = normalizedNodeName(child)) {
            "title" -> out += ChangelogBlock.Title(buildBlockText(child, linkColor))

            "p" -> {
                val text = buildBlockText(child, linkColor)
                out += ChangelogBlock.Paragraph(text, isLikelyHeading(text))
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val text = buildBlockText(child, linkColor)
                out += ChangelogBlock.Heading(text, name.substring(1).toInt(), isLikelyHeading(text))
            }

            "li" -> {
                out += ChangelogBlock.ListEntry(
                    text = buildListItemText(child, linkColor),
                    marker = listMarker(child),
                    depth = listDepth(child),
                )
                // Nested lists render as subsequent (deeper) blocks, after the <li> itself.
                val liChildren = child.childNodes
                for (j in 0 until liChildren.length) {
                    val lc = liChildren.item(j)
                    if (lc.nodeType == Node.ELEMENT_NODE) {
                        val n = normalizedNodeName(lc)
                        if (n == "ul" || n == "ol") collectBlocks(lc, linkColor, out)
                    }
                }
            }

            else -> collectBlocks(child, linkColor, out)
        }
    }
}

// Inline text of a block element (title / p / hN): its full descendant content with inline styling.
private fun buildBlockText(node: Node, linkColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.appendInlineChildren(node, linkColor)
    return builder.toAnnotatedString()
}

// Inline text of a list item: direct text is trimmed, nested <ul>/<ol> are excluded (rendered as their
// own blocks), everything else is appended inline.
private fun buildListItemText(li: Node, linkColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val children = li.childNodes
    for (j in 0 until children.length) {
        val c = children.item(j)
        when (c.nodeType) {
            Node.ELEMENT_NODE -> {
                val name = normalizedNodeName(c)
                if (name != "ul" && name != "ol") builder.appendInlineNode(c, linkColor)
            }

            Node.TEXT_NODE -> {
                val s = (c.textContent ?: "").replace(whitespaceRegex, " ").trim()
                if (s.isNotBlank()) builder.append(s)
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendInlineChildren(node: Node, linkColor: Color) {
    val children = node.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        when (child.nodeType) {
            Node.ELEMENT_NODE -> appendInlineNode(child, linkColor)

            Node.TEXT_NODE -> {
                val s = (child.textContent ?: "").replace(whitespaceRegex, " ")
                if (s.isNotBlank()) append(s)
            }
        }
    }
}

// Append an inline element's content, applying its own link (href) / bold / italic styling.
private fun AnnotatedString.Builder.appendInlineNode(node: Node, linkColor: Color) {
    val name = normalizedNodeName(node)
    val isBold = name == "b" || name == "strong"
    val isItalic = name == "i" || name == "em"

    val startIndex = length

    var pushedLink = false
    val attributes = node.attributes
    if (attributes != null) {
        for (a in 0 until attributes.length) {
            val attribute = attributes.item(a)
            if (attribute.nodeName == "href") {
                val url = sanitizeFeedUrl(attribute.nodeValue)
                if (url != null) {
                    pushLink(LinkAnnotation.Url(url))
                    pushStringAnnotation("URL", url)
                    pushStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold))
                    pushedLink = true
                }
            }
        }
    }

    appendInlineChildren(node, linkColor)

    val endIndex = length
    if (isBold && endIndex > startIndex) addStyle(SpanStyle(fontWeight = FontWeight.Bold), startIndex, endIndex)
    if (isItalic && endIndex > startIndex) addStyle(SpanStyle(fontStyle = FontStyle.Italic), startIndex, endIndex)
    if (pushedLink) {
        pop()
        pop()
        pop()
    }
}

private fun listDepth(li: Node): Int {
    var depth = 0
    var p: Node? = li.parentNode
    while (p != null) {
        val name = normalizedNodeName(p)
        if (name == "ul" || name == "ol") depth++
        p = p.parentNode
    }
    return depth
}

private fun listMarker(li: Node): String {
    val parent = li.parentNode
    return if (normalizedNodeName(parent) == "ol") {
        var idx = 1
        var s = parent.firstChild
        while (s != null) {
            if (s == li) break
            if (s.nodeType == Node.ELEMENT_NODE && normalizedNodeName(s) == "li") idx++
            s = s.nextSibling
        }
        "$idx."
    } else {
        "•"
    }
}

private fun headingStyle(level: Int, base: TextStyle): TextStyle = when (level) {
    1 -> base.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold)
    2 -> base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    3 -> base.copy(fontSize = 18.72.sp, fontWeight = FontWeight.Bold)
    4 -> base.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    5 -> base.copy(fontSize = 13.28.sp, fontWeight = FontWeight.Bold)
    6 -> base.copy(fontSize = 10.72.sp, fontWeight = FontWeight.Bold)
    else -> base
}

@Composable
fun Changelog(modifier: Modifier = Modifier, entry: String) {
    val localUriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val baseStyle = LocalTextStyle.current

    // Parse + build the render model once per entry (and per theme link colour), off the recomposition
    // path. Never crashes composition on malformed/hostile feed content.
    val blocks = remember(entry, linkColor) { parseChangelog(entry, linkColor) }

    SelectionContainer {
        ElevatedCard(modifier) {
            Column(Modifier.padding(16.dp)) {
                if (blocks == null) {
                    Text(text = entry)
                } else {
                    blocks.forEach { block ->
                        ChangelogBlockView(block, baseStyle, localUriHandler)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogBlockView(
    block: ChangelogBlock,
    baseStyle: TextStyle,
    localUriHandler: UriHandler,
) {
    val onClick: (Int) -> Unit = { offset ->
        block.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
            localUriHandler.openUri(annotation.item)
        }
    }

    when (block) {
        is ChangelogBlock.Title -> ClickableText(
            text = block.text,
            modifier = Modifier
                .semantics { heading() }
                .padding(vertical = 12.dp),
            onClick = onClick,
            style = typography.titleLarge,
        )

        is ChangelogBlock.Paragraph -> ClickableText(
            text = block.text,
            modifier = Modifier.padding(vertical = 12.dp),
            onClick = onClick,
            style = if (block.likelyHeading) typography.titleMedium else baseStyle,
        )

        is ChangelogBlock.Heading -> ClickableText(
            text = block.text,
            modifier = Modifier.padding(vertical = 12.dp),
            onClick = onClick,
            style = if (block.likelyHeading) typography.titleMedium else headingStyle(block.level, baseStyle),
        )

        is ChangelogBlock.ListEntry -> {
            val leftPadding = if (block.depth > 1) ((block.depth - 1) * 16).dp else 0.dp
            Row(modifier = Modifier.padding(start = leftPadding, top = 4.dp, bottom = 4.dp)) {
                Text(
                    block.marker,
                    modifier = Modifier.padding(end = 8.dp),
                    fontSize = 14.sp
                )

                ClickableText(
                    text = block.text,
                    onClick = onClick,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
