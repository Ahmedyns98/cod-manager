package com.westy.codmanager;

import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.finance.service.RemittanceCsvParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure parsing logic, tested without Spring or a database. Every case here is
 * something a real carrier export has actually contained.
 */
class RemittanceCsvParserTest {

    private final RemittanceCsvParser parser = new RemittanceCsvParser();

    private RemittanceCsvParser.Result parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "payout.csv");
    }

    @Test
    void readsACommaSeparatedFile() {
        var result = parse("""
                tracking,collected,fee
                yal-1,2500.00,500.00
                yal-2,3000.00,500.00""");

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).tracking()).isEqualTo("yal-1");
        assertThat(result.rows().get(0).collected()).isEqualByComparingTo("2500.00");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void readsASemicolonSeparatedFileWithFrenchHeaders() {
        var result = parse("""
                colis;montant;frais
                yal-1;2500,00;500,00""");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).collected()).isEqualByComparingTo("2500.00");
        assertThat(result.rows().get(0).fee()).isEqualByComparingTo("500.00");
    }

    @Test
    void stripsTheByteOrderMarkExcelWrites() {
        var result = parse("\uFEFFtracking,collected\nyal-1,1000");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).tracking()).isEqualTo("yal-1");
    }

    @Test
    void handlesThousandsSeparatorsInBothConventions() {
        var result = parse("""
                tracking,collected
                yal-1,"1 500,00"
                yal-2,"2,300.50"
                yal-3,4000""");

        assertThat(result.rows().get(0).collected()).isEqualByComparingTo("1500.00");
        assertThat(result.rows().get(1).collected()).isEqualByComparingTo("2300.50");
        assertThat(result.rows().get(2).collected()).isEqualByComparingTo("4000.00");
    }

    @Test
    void keepsDelimitersThatSitInsideQuotedFields() {
        var result = parse("""
                tracking;collected;fee
                "yal;1";2500,00;0""");

        assertThat(result.rows().get(0).tracking()).isEqualTo("yal;1");
    }

    @Test
    void oneBadRowDoesNotCostTheWholeFile() {
        var result = parse("""
                tracking,collected
                yal-1,2500.00
                ,3000.00
                yal-3,pas un montant
                yal-4,1000.00""");

        assertThat(result.rows()).hasSize(2);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).number()).isEqualTo(3);
        assertThat(result.errors().get(1).message()).contains("montant");
    }

    @Test
    void blankLinesAreSkipped() {
        var result = parse("tracking,collected\n\nyal-1,1000\n\n\n");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void aMissingFeeColumnDefaultsToZero() {
        var result = parse("tracking,collected\nyal-1,2500.00");

        assertThat(result.rows().get(0).fee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aFileWithoutATrackingColumnIsRejected() {
        assertThatThrownBy(() -> parse("montant,frais\n2500,500"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tracking");
    }

    @Test
    void anEmptyFileIsRejected() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no rows");
    }
}
