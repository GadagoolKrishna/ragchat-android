package com.ragchat.api.model

/**
 * Structural element extracted by a document parser.
 */
public sealed interface ParsedElement {
    /**
     * 1-based page number where this element originates, if applicable.
     */
    public val pageNumber: Int?

    /**
     * Section or chapter title containing this element, if determined.
     */
    public val section: String?

    /**
     * Standard running text block.
     *
     * @property text Extracted body text.
     * @property pageNumber 1-based page number if available.
     * @property section Section or heading context if available.
     */
    public data class Text(
        val text: String,
        override val pageNumber: Int? = null,
        override val section: String? = null,
    ) : ParsedElement

    /**
     * Section header or title.
     *
     * @property title Heading text.
     * @property level Heading depth level (1 for H1, 2 for H2, etc.).
     * @property pageNumber 1-based page number if available.
     * @property section Section or heading context if available.
     */
    public data class Heading(
        val title: String,
        val level: Int,
        override val pageNumber: Int? = null,
        override val section: String? = null,
    ) : ParsedElement

    /**
     * Tabular data extracted from the document.
     *
     * @property headers Column names.
     * @property rows Matrix of row cells.
     * @property pageNumber 1-based page number if available.
     * @property section Section or heading context if available.
     */
    public data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        override val pageNumber: Int? = null,
        override val section: String? = null,
    ) : ParsedElement

    /**
     * Extracted image data or figure.
     *
     * @property mimeType Image format (e.g., image/png, image/jpeg).
     * @property imageBytes Raw binary data of the image.
     * @property altText Optional caption or alt text.
     * @property pageNumber 1-based page number if available.
     * @property section Section or heading context if available.
     */
    public class Image(
        public val mimeType: String,
        public val imageBytes: ByteArray,
        public val altText: String? = null,
        override val pageNumber: Int? = null,
        override val section: String? = null,
    ) : ParsedElement {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            if (mimeType != other.mimeType) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (altText != other.altText) return false
            if (pageNumber != other.pageNumber) return false
            if (section != other.section) return false
            return true
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + imageBytes.contentHashCode()
            result = 31 * result + (altText?.hashCode() ?: 0)
            result = 31 * result + (pageNumber?.hashCode() ?: 0)
            result = 31 * result + (section?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Explicit page boundary indicator.
     *
     * @property pageNumber 1-based index of the completed page.
     */
    public data class PageBreak(
        override val pageNumber: Int,
        override val section: String? = null,
    ) : ParsedElement
}
