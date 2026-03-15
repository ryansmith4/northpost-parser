package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.guidedbyte.address.model.AddressComponents;
import com.guidedbyte.address.model.AddressComponents.AddressType;
import com.guidedbyte.address.model.AddressComponents.ParsingMode;
import com.guidedbyte.address.service.AddressParserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Comprehensive test suite for the Canadian Address Parser */
class AddressParserTest {

    private AddressParserService parser;

    @BeforeEach
    void setUp() {
        parser = new AddressParserService();
    }

    @Nested
    @DisplayName("Civic Address Parsing")
    class CivicAddressTests {

        @Test
        @DisplayName("Should parse complete civic address")
        void shouldParseCompleteCivicAddress() {
            String address = """
                JOHN SMITH
                123 MAIN STREET
                TORONTO ON M5V 2Y7
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressType()).isEqualTo(AddressType.CIVIC);
            assertThat(result.components().addressee()).isEqualTo("JOHN SMITH");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("STREET");
            assertThat(result.components().municipality()).isEqualTo("TORONTO");
            assertThat(result.components().province()).isEqualTo("ON");
            assertThat(result.components().postalCode()).isEqualTo("M5V 2Y7");
        }

        @Test
        @DisplayName("Should parse civic address with unit number")
        void shouldParseCivicAddressWithUnit() {
            String address = """
                MARY JOHNSON
                APT 5 456 ELM AVENUE
                VANCOUVER BC V6B 2K1
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("5");
            assertThat(result.components().streetNumber()).isEqualTo("456");
            assertThat(result.components().streetName()).isEqualTo("ELM");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
        }

        @Test
        @DisplayName("Should parse civic address with hyphenated unit")
        void shouldParseCivicAddressWithHyphenatedUnit() {
            String address = """
                BOB WILSON
                10-123 MAIN STREET
                CALGARY AB T2P 1K3
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("10");
            assertThat(result.components().streetNumber()).isEqualTo("123");
        }

        @Test
        @DisplayName("Should parse street type as street name")
        void shouldParseStreetTypeAsStreetName() {
            String address = """
                JANE DOE
                789 THE PARKWAY
                OTTAWA ON K1A 0A6
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetNumber()).isEqualTo("789");
            assertThat(result.components().streetName()).isEqualTo("THE PARKWAY");
        }
    }

    @Nested
    @DisplayName("French Address Parsing")
    class FrenchAddressTests {

        @Test
        @DisplayName("Should parse French address with type before name")
        void shouldParseFrenchAddressTypeFirst() {
            String address = """
                JEAN DUPONT
                123 RUE PRINCIPALE
                MONTRÉAL QC H3Z 2Y7
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
        }

        @Test
        @DisplayName("Should parse French ordinal street name")
        void shouldParseFrenchOrdinalStreetName() {
            String address = """
                MARIE TREMBLAY
                456 1ÈRE RUE
                QUÉBEC QC G1K 7X4
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetNumber()).isEqualTo("456");
        }

        @Test
        @DisplayName("Should handle mixed French-English usage")
        void shouldHandleMixedFrenchEnglish() {
            String address = """
                PIERRE MARTIN
                789 MAIN RUE
                MONTREAL QC H3Z 2Y7
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("RUE");
        }
    }

    @Nested
    @DisplayName("Street Type Disambiguation")
    class StreetTypeDisambiguationTests {

        // ---- Rule 1: "THE" prefix — street type word used as proper name ----

        @Test
        @DisplayName("Rule 1: THE PARKWAY — parkway is the name, not type")
        void thePrefix_parkway() {
            var result = parser.parseAddress("JANE DOE\n789 THE PARKWAY\nOTTAWA ON K1A 0A6");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("THE PARKWAY");
            assertThat(result.components().streetType()).isEmpty();
        }

        @Test
        @DisplayName("Rule 1: THE BOULEVARD — boulevard is the name, not type")
        void thePrefix_boulevard() {
            var result = parser.parseAddress("JOHN SMITH\n100 THE BOULEVARD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("THE BOULEVARD");
            assertThat(result.components().streetType()).isEmpty();
        }

        @Test
        @DisplayName("Rule 1: THE DRIVE — drive is the name, not type")
        void thePrefix_drive() {
            var result = parser.parseAddress("ALICE WONG\n55 THE DRIVE\nOTTAWA ON K1A 0A6");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("THE DRIVE");
            assertThat(result.components().streetType()).isEmpty();
        }

        @Test
        @DisplayName("Rule 1: THE QUEENSWAY — not a standard type, but still handled")
        void thePrefix_queensway() {
            var result = parser.parseAddress("BOB JONES\n200 THE QUEENSWAY\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("THE QUEENSWAY");
            assertThat(result.components().streetType()).isEmpty();
        }

        // ---- Rule 2: Two consecutive street types — last is type, first is name ----

        @Test
        @DisplayName("Rule 2: PARK DRIVE — park is name, drive is type")
        void twoConsecutiveTypes_parkDrive() {
            var result = parser.parseAddress("JOHN DOE\n123 PARK DRIVE\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PARK");
            assertThat(result.components().streetType()).isEqualTo("DRIVE");
        }

        @Test
        @DisplayName("Rule 2: COURT LANE — court is name, lane is type")
        void twoConsecutiveTypes_courtLane() {
            var result = parser.parseAddress("JANE SMITH\n456 COURT LANE\nVANCOUVER BC V6B 2K1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("COURT");
            assertThat(result.components().streetType()).isEqualTo("LANE");
        }

        @Test
        @DisplayName("Rule 2: ABBEY ROAD — abbey is name, road is type")
        void twoConsecutiveTypes_abbeyRoad() {
            var result = parser.parseAddress("RINGO STARR\n3 ABBEY ROAD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("ABBEY");
            assertThat(result.components().streetType()).isEqualTo("ROAD");
        }

        @Test
        @DisplayName("Rule 2: Normal case — MAIN STREET is not two-type (MAIN is not a type)")
        void normalCase_mainStreet() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN STREET\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("STREET");
        }

        // ---- Rule 3: French linking particles — no double generic ----

        @Test
        @DisplayName("Rule 3: AVENUE DU PARC — parc is name via linking particle")
        void frenchParticle_avenueDuParc() {
            var result = parser.parseAddress("JEAN DUPONT\n123 AVENUE DU PARC\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetName()).isEqualTo("DU PARC");
        }

        @Test
        @DisplayName("Rule 3: CHEMIN DE LA CÔTE-DES-NEIGES — côte is name via particle")
        void frenchParticle_cheminCote() {
            var result = parser.parseAddress("MARIE MARTIN\n456 CHEMIN DE LA CÔTE-DES-NEIGES\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("CHEMIN");
            assertThat(result.components().streetName()).isEqualTo("DE LA CÔTE-DES-NEIGES");
        }

        @Test
        @DisplayName("Rule 3: BOULEVARD DE LA CÔTE-VERTU — côte is name via particle")
        void frenchParticle_boulevardCoteVertu() {
            var result = parser.parseAddress("PIERRE ROY\n789 BOULEVARD DE LA CÔTE-VERTU\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("BOULEVARD");
            assertThat(result.components().streetName()).isEqualTo("DE LA CÔTE-VERTU");
        }

        @Test
        @DisplayName("Rule 3: RUE PRINCIPALE — simple French, no particle needed")
        void frenchSimple_ruePrincipale() {
            var result = parser.parseAddress("JEAN DUPONT\n123 RUE PRINCIPALE\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
        }
    }

    @Nested
    @DisplayName("Postal Box Address Parsing")
    class PostalBoxTests {

        @Test
        @DisplayName("Should parse PO Box address")
        void shouldParsePoBoxAddress() {
            String address = """
                BUSINESS CORP
                PO BOX 1234 STN A
                CALGARY AB T2P 1K3
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressType()).isEqualTo(AddressType.POSTAL_BOX);
            assertThat(result.components().postalBoxNumber()).isEqualTo("1234");
        }

        @Test
        @DisplayName("Should parse simplified box format")
        void shouldParseSimplifiedBoxFormat() {
            String address = """
                PERSON NAME
                BOX 567
                TOWN AB T1T 1T1
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressType()).isEqualTo(AddressType.POSTAL_BOX);
            assertThat(result.components().postalBoxNumber()).isEqualTo("567");
        }

        @Test
        @DisplayName("Should parse French CP address")
        void shouldParseFrenchCpAddress() {
            String address = """
                ENTREPRISE LTÉE
                CP 890 SUCC B
                MONTRÉAL QC H1H 1H1
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressType()).isEqualTo(AddressType.POSTAL_BOX);
            assertThat(result.components().postalBoxNumber()).isEqualTo("890");
        }
    }

    @Nested
    @DisplayName("Rural Address Parsing")
    class RuralAddressTests {

        @Test
        @DisplayName("Should parse rural route address")
        void shouldParseRuralRouteAddress() {
            String address = """
                FARM OWNER
                RR 5 STN MAIN
                RURAL TOWN AB T0L 1K0
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressType()).isEqualTo(AddressType.RURAL_ROUTE);
            assertThat(result.components().ruralRoute()).isEqualTo("5");
        }

        @Test
        @DisplayName("Should parse rural route with site info")
        void shouldParseRuralRouteWithSite() {
            String address = """
                RURAL RESIDENT
                SITE 6 COMP 10
                RR 2
                SMALL TOWN SK S0S 0S0
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().ruralRoute()).isEqualTo("2");
            assertThat(result.components().siteInfo()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Care Of Parsing")
    class CareOfTests {

        @Test
        @DisplayName("Should parse care of address")
        void shouldParseCareOfAddress() {
            String address = """
                JOHN SMITH
                C/O JANE DOE
                123 MAIN STREET
                TORONTO ON M5V 2Y7
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().careOf()).isEqualTo("JANE DOE");
        }

        @Test
        @DisplayName("Should parse French care of")
        void shouldParseFrenchCareOf() {
            String address = """
                JEAN DUPONT
                A/S MARIE MARTIN
                456 RUE EXAMPLE
                QUÉBEC QC G1G 1G1
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().careOf()).isEqualTo("MARIE MARTIN");
        }
    }

    @Nested
    @DisplayName("Country Specification")
    class CountryTests {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON M1C 1A4\nCANADA",
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON M1C 1A4 CANADA"
                })
        @DisplayName("Should parse addresses with country in various positions")
        void shouldParseAddressesWithCountry(String address) {
            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().country()).isEqualTo("CANADA");
            assertThat(result.warnings()).contains("Country specification is redundant for Canadian addresses");
        }
    }

    @Nested
    @DisplayName("Parsing Modes")
    class ParsingModeTests {

        @Test
        @DisplayName("Lenient mode should accept incomplete addresses")
        void lenientModeShouldAcceptIncompleteAddresses() {
            String incompleteAddress = """
                PERSON NAME
                SOME STREET
                """;

            var result = parser.parseAddress(incompleteAddress);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressee()).isEqualTo("PERSON NAME");
            assertThat(result.components().isComplete()).isFalse();
        }

        @Test
        @DisplayName("Should parse complete address successfully")
        void shouldParseCompleteAddress() {
            String completeAddress = """
                JOHN SMITH
                123 MAIN STREET
                TORONTO ON M5V 2Y7
                """;

            var result = parser.parseAddress(completeAddress);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().isComplete()).isTrue();
        }

        @Test
        @DisplayName("Strict mode should reject incomplete addresses")
        void strictModeShouldRejectIncompleteAddresses() {
            String incompleteAddress = """
                PERSON NAME
                SOME STREET
                """;

            var result = parser.parseAddress(incompleteAddress, AddressParserService.ParsingStrategy.STRICT);

            assertThat(result.successful()).isFalse();
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors()).contains("Address is not complete for mailing purposes");
        }

        @Test
        @DisplayName("Strict mode should accept complete addresses")
        void strictModeShouldAcceptCompleteAddresses() {
            String completeAddress = """
                JOHN SMITH
                123 MAIN STREET
                TORONTO ON M5V 2Y7
                """;

            var result = parser.parseAddress(completeAddress, AddressParserService.ParsingStrategy.STRICT);

            assertThat(result.successful()).isTrue();
            assertThat(result.modeUsed()).isEqualTo(ParsingMode.STRICT);
            assertThat(result.components().isComplete()).isTrue();
        }

        @Test
        @DisplayName("Strict-then-lenient should fall back on incomplete address")
        void strictThenLenientShouldFallBack() {
            String incompleteAddress = """
                PERSON NAME
                SOME STREET
                """;

            var result =
                    parser.parseAddress(incompleteAddress, AddressParserService.ParsingStrategy.STRICT_THEN_LENIENT);

            assertThat(result.successful()).isTrue();
            assertThat(result.modeUsed()).isEqualTo(ParsingMode.LENIENT);
            assertThat(result.components().addressee()).isEqualTo("PERSON NAME");
            assertThat(result.components().isComplete()).isFalse();
        }

        @Test
        @DisplayName("Strict-then-lenient should use strict for complete address")
        void strictThenLenientShouldUseStrictForComplete() {
            String completeAddress = """
                JOHN SMITH
                123 MAIN STREET
                TORONTO ON M5V 2Y7
                """;

            var result = parser.parseAddress(completeAddress, AddressParserService.ParsingStrategy.STRICT_THEN_LENIENT);

            assertThat(result.successful()).isTrue();
            assertThat(result.modeUsed()).isEqualTo(ParsingMode.STRICT);
            assertThat(result.components().isComplete()).isTrue();
        }
    }

    @Nested
    @DisplayName("Messy Input Handling")
    class MessyInputTests {

        @Test
        @DisplayName("Should handle excessive whitespace")
        void shouldHandleExcessiveWhitespace() {
            String messyAddress = """
                JOHN     SMITH
                123   MAIN    STREET
                TORONTO    ON    M5V 2Y7
                """;

            var result = parser.parseAddress(messyAddress);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressee()).isEqualTo("JOHN SMITH");
        }

        @Test
        @DisplayName("Should handle mixed case")
        void shouldHandleMixedCase() {
            String mixedCaseAddress = """
                john smith
                123 main street
                toronto on m5v 2y7
                """;

            var result = parser.parseAddress(mixedCaseAddress);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressee()).isEqualTo("JOHN SMITH");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
        }

        @Test
        @DisplayName("Should handle postal code without space")
        void shouldHandlePostalCodeWithoutSpace() {
            String address = """
                JANE DOE
                456 ELM STREET
                VANCOUVER BC V6B2K1
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().postalCode()).isEqualTo("V6B 2K1");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            var result = parser.parseAddress(null);

            assertThat(result.successful()).isFalse();
            assertThat(result.errors()).contains("Address text cannot be null or empty");
        }

        @Test
        @DisplayName("Should handle empty input")
        void shouldHandleEmptyInput() {
            var result = parser.parseAddress("");

            assertThat(result.successful()).isFalse();
            assertThat(result.errors()).contains("Address text cannot be null or empty");
        }

        @Test
        @DisplayName("Should handle single line address")
        void shouldHandleSingleLineAddress() {
            String singleLine = "JOHN SMITH 123 MAIN ST TORONTO ON M5V2Y7";

            var result = parser.parseAddress(singleLine);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().hasAddressee()).isTrue();
        }

        @Test
        @DisplayName("Should handle address with only postal code")
        void shouldHandleOnlyPostalCode() {
            String onlyPostalCode = "M5V 2Y7";

            var result = parser.parseAddress(onlyPostalCode);

            assertThat(result.successful()).isTrue();
            assertThat(result.components().postalCode()).isEqualTo("M5V 2Y7");
            assertThat(result.components().isComplete()).isFalse();
        }
    }

    @Nested
    @DisplayName("Batch Processing")
    class BatchProcessingTests {

        @Test
        @DisplayName("Should process multiple addresses")
        void shouldProcessMultipleAddresses() {
            var addresses = List.of(
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON M5V 2Y7",
                    "JANE DOE\nPO BOX 456\nVANCOUVER BC V6B 2K1",
                    "INCOMPLETE ADDRESS\nSOMEWHERE");

            var results = parser.parseAddresses(addresses);

            assertThat(results).hasSize(3);
            assertThat(results.get(0).successful()).isTrue();
            assertThat(results.get(1).successful()).isTrue();
            assertThat(results.get(2).successful()).isTrue();
            assertThat(results.get(2).components().isComplete()).isFalse();
        }

        @Test
        @DisplayName("Should generate batch statistics")
        void shouldGenerateBatchStatistics() {
            var addresses = List.of(
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON M5V 2Y7",
                    "JANE DOE\n456 ELM ST\nVANCOUVER BC V6B 2K1",
                    "INCOMPLETE\nSOMEWHERE");

            var results = parser.parseAddresses(addresses);
            var stats = parser.getBatchStatistics(results);

            assertThat(stats.totalAddresses()).isEqualTo(3);
            assertThat(stats.successfulParses()).isEqualTo(3);
            assertThat(stats.completeAddresses()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Address Components Model")
    class AddressComponentsModelTests {

        @Test
        @DisplayName("Should build components using builder pattern")
        void shouldBuildComponentsUsingBuilder() {
            var components = AddressComponents.builder()
                    .addressee("JOHN SMITH")
                    .streetNumber("123")
                    .streetName("MAIN")
                    .streetType("STREET")
                    .municipality("TORONTO")
                    .province("ON")
                    .postalCode("M5V 2Y7")
                    .addressType(AddressType.CIVIC)
                    .parsingMode(ParsingMode.STRICT)
                    .isComplete(true)
                    .build();

            assertThat(components.addressee()).isEqualTo("JOHN SMITH");
            assertThat(components.hasAddressee()).isTrue();
            assertThat(components.isMailingValid()).isTrue();
        }

        @Test
        @DisplayName("Should format street address correctly")
        void shouldFormatStreetAddressCorrectly() {
            var components = AddressComponents.builder()
                    .unitNumber("5")
                    .streetNumber("123")
                    .streetName("MAIN")
                    .streetType("STREET")
                    .streetDirection("SE")
                    .addressType(AddressType.CIVIC)
                    .build();

            var formatted = components.getFormattedStreetAddress();

            assertThat(formatted).isPresent();
            assertThat(formatted.get()).isEqualTo("5-123 MAIN STREET SE");
        }

        @Test
        @DisplayName("Should format complete address correctly")
        void shouldFormatCompleteAddressCorrectly() {
            var components = AddressComponents.builder()
                    .addressee("JOHN SMITH")
                    .careOf("JANE DOE")
                    .streetNumber("123")
                    .streetName("MAIN")
                    .streetType("STREET")
                    .municipality("TORONTO")
                    .province("ON")
                    .postalCode("M5V 2Y7")
                    .addressType(AddressType.CIVIC)
                    .build();

            String formatted = components.getFormattedAddress();

            assertThat(formatted).contains("JOHN SMITH");
            assertThat(formatted).contains("C/O JANE DOE");
            assertThat(formatted).contains("123 MAIN STREET");
            assertThat(formatted).contains("TORONTO ON  M5V 2Y7");
        }

        @Test
        @DisplayName("Should validate mailing requirements")
        void shouldValidateMailingRequirements() {
            var completeComponents = AddressComponents.builder()
                    .addressee("JOHN SMITH")
                    .streetNumber("123")
                    .streetName("MAIN")
                    .municipality("TORONTO")
                    .province("ON")
                    .postalCode("M5V 2Y7")
                    .addressType(AddressType.CIVIC)
                    .build();

            var incompleteComponents = AddressComponents.builder()
                    .addressee("JOHN SMITH")
                    .streetName("MAIN")
                    .addressType(AddressType.CIVIC)
                    .build();

            assertThat(completeComponents.isMailingValid()).isTrue();
            assertThat(incompleteComponents.isMailingValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Validation and Warnings")
    class ValidationTests {

        @Test
        @DisplayName("Should generate warning for redundant country")
        void shouldGenerateWarningForRedundantCountry() {
            String address = """
                JOHN SMITH
                123 MAIN STREET
                TORONTO ON M5V 2Y7
                CANADA
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.warnings()).contains("Country specification is redundant for Canadian addresses");
        }

        @Test
        @DisplayName("Should generate warning for incomplete address")
        void shouldGenerateWarningForIncompleteAddress() {
            String incompleteAddress = """
                JOHN SMITH
                SOME STREET
                TORONTO
                """;

            var result = parser.parseAddress(incompleteAddress);

            assertThat(result.successful()).isTrue();
            assertThat(result.warnings()).contains("Address may be incomplete for mailing purposes");
        }

        @Test
        @DisplayName("Should generate warning for street name without number")
        void shouldGenerateWarningForStreetNameWithoutNumber() {
            String address = """
                JOHN SMITH
                MAIN STREET
                TORONTO ON M5V 2Y7
                """;

            var result = parser.parseAddress(address);

            assertThat(result.successful()).isTrue();
            assertThat(result.warnings()).contains("Street name provided without street number");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should parse large batch efficiently")
        void shouldParseLargeBatchEfficiently() {
            var addresses = java.util.stream.IntStream.range(0, 1000)
                    .mapToObj(i -> String.format("""
                    PERSON %d
                    %d MAIN STREET
                    TORONTO ON M5V 2Y7
                    """, i, i + 100))
                    .toList();

            long startTime = System.currentTimeMillis();
            var results = parser.parseAddresses(addresses);
            long endTime = System.currentTimeMillis();

            assertThat(results).hasSize(1000);
            assertThat(results.stream().allMatch(AddressParserService.ParsingResult::successful))
                    .isTrue();

            long duration = endTime - startTime;
            assertThat(duration).isLessThan(10000);

            System.out.printf(
                    "Parsed 1000 addresses in %d ms (%.2f ms per address)%n", duration, (double) duration / 1000);
        }
    }
}
