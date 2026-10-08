package com.carddemo.batch.statement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatementHtmlEscapeTest {

    @Test
    void batStm01_escapesMarkupCharacters() {
        assertThat(StatementGenerator.escapeHtml("<script>alert('x') & \"y\"</script>"))
                .isEqualTo("&lt;script&gt;alert(&#39;x&#39;) &amp; &quot;y&quot;&lt;/script&gt;");
    }

    @Test
    void batStm01_escapedLineIsShortenedToFitHtmlRecord() {
        String line = StatementGenerator.escapedLine("<p>", "<".repeat(40), "</p>", StatementGenerator.HTML_LRECL);
        assertThat(line).hasSizeLessThanOrEqualTo(StatementGenerator.HTML_LRECL).startsWith("<p>&lt;").endsWith("&lt;</p>");
    }
}
