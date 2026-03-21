package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.guidedbyte.address.model.AddressComponents;
import com.guidedbyte.address.model.AddressComponents.AddressType;
import com.guidedbyte.address.model.AddressComponents.ParsingMode;
import com.guidedbyte.address.model.NormalizationStrategy;
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

        // ---- Rule 1 (French): Article/adjective prefix — type word is part of the name ----

        @Test
        @DisplayName("Rule 1: GRANDE ALLÉE — allée is the name, not type")
        void frenchPrefix_grandeAllee() {
            var result = parser.parseAddress("JEAN DUPONT\n220 GRANDE ALLÉE E\nQUÉBEC QC G1R 2J1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRANDE ALLÉE");
            assertThat(result.components().streetType()).isEmpty();
            assertThat(result.components().streetDirection()).isEqualTo("E");
        }

        @Test
        @DisplayName("Rule 1: GRANDE CÔTE — côte is French-only, whole phrase is the name")
        void frenchPrefix_grandeCote() {
            var result = parser.parseAddress("MARIE MARTIN\n1620 GRANDE CÔTE\nLANORAIE QC J0K 1E0");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRANDE CÔTE");
            assertThat(result.components().streetType()).isEmpty();
        }

        @Test
        @DisplayName("Rule 1: GRAND RANG — rang is French-only, whole phrase is the name")
        void frenchPrefix_grandRang() {
            var result = parser.parseAddress("JEAN DUPONT\n8605 GRAND RANG\nSAINT-HYACINTHE QC J2T 5H1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRAND RANG");
            assertThat(result.components().streetType()).isEmpty();
        }

        @Test
        @DisplayName("Rule 1: GRAND BOULEVARD in QC — proper name, no type")
        void frenchPrefix_grandBoulevard_qc() {
            var result = parser.parseAddress("JEAN DUPONT\n4039 GRAND BOULEVARD\nMONTRÉAL QC H1H 1H1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRAND BOULEVARD");
            assertThat(result.components().streetType()).isEmpty();
        }

        @Test
        @DisplayName("Rule 1: GRAND BOULEVARD in ON — splits normally (not QC)")
        void frenchPrefix_grandBoulevard_on() {
            var result = parser.parseAddress("JOHN SMITH\n100 GRAND BOULEVARD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRAND");
            assertThat(result.components().streetType()).isEqualTo("BOULEVARD");
        }

        @Test
        @DisplayName("Rule 1: GRAND AVE — English-only type, guard does NOT fire")
        void frenchPrefix_grandAve_englishType() {
            var result = parser.parseAddress("JOHN SMITH\n100 GRAND AVE\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRAND");
            assertThat(result.components().streetType()).isEqualTo("AVE");
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
    @DisplayName("Direction Suffix Preference")
    class DirectionSuffixPreferenceTests {

        @Test
        @DisplayName("Prefix + suffix → suffix wins, prefix stays in street name")
        void prefixAndSuffix_suffixWins() {
            var result = parser.parseAddress("JOHN SMITH\n1154 E MILLBOURNE RD NW\nEDMONTON AB T6K 1X2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetDirection()).isEqualTo("NW");
            assertThat(result.components().streetName()).isEqualTo("E MILLBOURNE");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        @Test
        @DisplayName("Prefix only → prefix used as direction")
        void prefixOnly_prefixUsed() {
            var result = parser.parseAddress("JOHN SMITH\n1154 E MILLBOURNE RD\nEDMONTON AB T6K 1X2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetDirection()).isEqualTo("E");
            assertThat(result.components().streetName()).isEqualTo("MILLBOURNE");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        @Test
        @DisplayName("Suffix only → suffix used (unchanged behavior)")
        void suffixOnly_noChange() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST N\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetDirection()).isEqualTo("N");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Prefix only (no suffix) → prefix used (unchanged behavior)")
        void prefixOnlyNoSuffix_noChange() {
            var result = parser.parseAddress("JOHN SMITH\n123 N MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetDirection()).isEqualTo("N");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Prefix + trailing unit with direction → trailing unit direction preserved")
        void prefixAndTrailingUnitDirection_trailingUnitWins() {
            var result = parser.parseAddress("JOHN SMITH\n123 E MAIN DR NW APT 5\nEDMONTON AB T6K 1X2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetDirection()).isEqualTo("NW");
            assertThat(result.components().unitNumber()).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("Expanded Street Types")
    class ExpandedStreetTypeTests {

        @Test
        @DisplayName("GD type recognized as Gardens")
        void gdTypeRecognized() {
            var result = parser.parseAddress("JOHN SMITH\n123 MORNINGSIDE GD SW\nCALGARY AB T2X 1X2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("MORNINGSIDE");
            assertThat(result.components().streetType()).isEqualTo("GD");
            assertThat(result.components().streetDirection()).isEqualTo("SW");
        }

        @Test
        @DisplayName("GD does not trigger general delivery on civic address")
        void gdDoesNotTriggerGeneralDelivery() {
            var result = parser.parseAddress("JOHN SMITH\n123 MORNINGSIDE GD\nCALGARY AB T2X 1X2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressType()).isEqualTo(AddressType.CIVIC);
        }

        @Test
        @DisplayName("PKWY type recognized as Parkway")
        void pkwyTypeRecognized() {
            var result = parser.parseAddress("JANE DOE\n456 RIVERSIDE PKWY\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("RIVERSIDE");
            assertThat(result.components().streetType()).isEqualTo("PKWY");
        }

        @Test
        @DisplayName("CREST type recognized")
        void crestTypeRecognized() {
            var result = parser.parseAddress("BOB SMITH\n789 HILLVIEW CREST\nCALGARY AB T2X 1X2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("HILLVIEW");
            assertThat(result.components().streetType()).isEqualTo("CREST");
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
        @DisplayName("Should return full street name (name + type + direction)")
        void shouldReturnFullStreetName() {
            var components = AddressComponents.builder()
                    .streetName("EVERITT")
                    .streetType("DRIVE")
                    .streetDirection("NORTH")
                    .build();

            assertThat(components.getFullStreetName()).isPresent();
            assertThat(components.getFullStreetName().get()).isEqualTo("EVERITT DRIVE NORTH");
        }

        @Test
        @DisplayName("Full street name with name only (no type or direction)")
        void fullStreetNameOnly() {
            var components =
                    AddressComponents.builder().streetName("GRANDE ALLÉE").build();

            assertThat(components.getFullStreetName()).isPresent();
            assertThat(components.getFullStreetName().get()).isEqualTo("GRANDE ALLÉE");
        }

        @Test
        @DisplayName("Full street name is empty when no street name")
        void fullStreetNameEmpty() {
            var components = AddressComponents.builder().build();
            assertThat(components.getFullStreetName()).isEmpty();
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

    @Nested
    @DisplayName("Normalization")
    class NormalizationTests {

        @Test
        @DisplayName("should normalize street type to full form")
        void shouldNormalizeStreetTypeToFullForm() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nTORONTO ON M5V 2Y7");
            var normalized = result.components().normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.streetType()).isEqualTo("STREET");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("should normalize street type to abbreviated form")
        void shouldNormalizeStreetTypeToAbbreviated() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN STREET\nTORONTO ON M5V 2Y7");
            var normalized = result.components().normalize(NormalizationStrategy.ABBREVIATED);
            assertThat(normalized.streetType()).isEqualTo("ST");
            assertThat(result.components().streetType()).isEqualTo("STREET");
        }

        @Test
        @DisplayName("should normalize direction to full form")
        void shouldNormalizeDirectionToFullForm() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST N\nTORONTO ON M5V 2Y7");
            var normalized = result.components().normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.streetDirection()).isEqualTo("NORTH");
            assertThat(result.components().streetDirection()).isEqualTo("N");
        }

        @Test
        @DisplayName("should normalize direction to abbreviated form")
        void shouldNormalizeDirectionToAbbreviated() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST NORTH\nTORONTO ON M5V 2Y7");
            var normalized = result.components().normalize(NormalizationStrategy.ABBREVIATED);
            assertThat(normalized.streetDirection()).isEqualTo("N");
            assertThat(result.components().streetDirection()).isEqualTo("NORTH");
        }

        @Test
        @DisplayName("should normalize French street type to full form")
        void shouldNormalizeFrenchStreetType() {
            var result = parser.parseAddress("JEAN DUPONT\n123 BOUL DES ÉRABLES\nMONTRÉAL QC H2X 1Y4");
            var normalized = result.components().normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.streetType()).isEqualTo("BOULEVARD");
        }

        @Test
        @DisplayName("should normalize French direction O to full form")
        void shouldNormalizeFrenchDirectionO() {
            var result = parser.parseAddress("JEAN DUPONT\n123 RUE PRINCIPALE O\nMONTRÉAL QC H2X 1Y4");
            var normalized = result.components().normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.streetDirection()).isEqualTo("OUEST");
        }

        @Test
        @DisplayName("should preserve non-type fields unchanged")
        void shouldPreserveOtherFields() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST N\nTORONTO ON M5V 2Y7");
            var normalized = result.components().normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.addressee()).isEqualTo("JOHN SMITH");
            assertThat(normalized.streetNumber()).isEqualTo("123");
            assertThat(normalized.streetName()).isEqualTo("MAIN");
            assertThat(normalized.municipality()).isEqualTo("TORONTO");
            assertThat(normalized.province()).isEqualTo("ON");
            assertThat(normalized.postalCode()).isEqualTo("M5V 2Y7");
        }

        @Test
        @DisplayName("should leave unknown types unchanged")
        void shouldLeaveUnknownTypesUnchanged() {
            var components = AddressComponents.builder()
                    .streetType("UNKNOWNTYPE")
                    .streetDirection("XYZ")
                    .build();
            var normalized = components.normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.streetType()).isEqualTo("UNKNOWNTYPE");
            assertThat(normalized.streetDirection()).isEqualTo("XYZ");
        }

        @Test
        @DisplayName("should handle empty components gracefully")
        void shouldHandleEmptyComponents() {
            var components = AddressComponents.builder().build();
            var normalized = components.normalize(NormalizationStrategy.FULL_FORM);
            assertThat(normalized.streetType()).isEmpty();
            assertThat(normalized.streetDirection()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Lettered/Numbered Avenue Parsing")
    class LetteredAvenueTests {

        @Test
        @DisplayName("SK: AVENUE P with direction S")
        void skAvenueP_directionS() {
            var result = parser.parseAddress("402 AVENUE P S\nSASKATOON SK S7M 2L9");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE P");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetDirection()).isEqualTo("S");
        }

        @Test
        @DisplayName("SK: AVENUE A with direction N")
        void skAvenueA_directionN() {
            var result = parser.parseAddress("135 AVENUE A N\nSASKATOON SK S7M 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE A");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetDirection()).isEqualTo("N");
        }

        @Test
        @DisplayName("SK: AVENUE B with NE direction")
        void skAvenueB_directionNE() {
            var result = parser.parseAddress("5 AVENUE B NE\nMOOSE JAW SK S6H 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE B");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetDirection()).isEqualTo("NE");
        }

        @Test
        @DisplayName("ON: AVENUE N (no direction — N is the street letter)")
        void onAvenueN_noDirection() {
            var result = parser.parseAddress("1309 AVENUE N\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE N");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("ON: AVENUE S (no direction — S is the street letter)")
        void onAvenueS_noDirection() {
            var result = parser.parseAddress("1351 AVENUE S\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE S");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("ON: AVENUE O (no direction — O is the street letter)")
        void onAvenueO_noDirection() {
            var result = parser.parseAddress("2047 AVENUE O\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE O");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("ON: AVENUE + number")
        void onAvenueNumber() {
            var result = parser.parseAddress("149 AVENUE 3\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE 3");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
        }

        @Test
        @DisplayName("ON: AVENUE + alphanumeric")
        void onAvenueAlphanumeric() {
            var result = parser.parseAddress("149 AVENUE 4A\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE 4A");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
        }

        @Test
        @DisplayName("AVE abbreviation")
        void aveAbbreviation() {
            var result = parser.parseAddress("402 AVE P S\nSASKATOON SK S7M 2L9");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVE P");
            assertThat(result.components().streetType()).isEqualTo("AVE");
            assertThat(result.components().streetDirection()).isEqualTo("S");
        }

        @Test
        @DisplayName("With unit: APT + AVENUE letter")
        void withUnit_aptAvenueLetter() {
            var result = parser.parseAddress("APT 27 402 AVENUE P S\nSASKATOON SK S7M 2L9");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("27");
            assertThat(result.components().streetNumber()).isEqualTo("402");
            assertThat(result.components().streetName()).isEqualTo("AVENUE P");
            assertThat(result.components().streetDirection()).isEqualTo("S");
        }

        @Test
        @DisplayName("Regression: AVENUE DU PARC — French type-first split still works")
        void regression_avenueDuParc() {
            var result = parser.parseAddress("123 AVENUE DU PARC\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("DU PARC");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
        }

        @Test
        @DisplayName("Regression: RUE PRINCIPALE — French type-first unaffected")
        void regression_ruePrincipale() {
            var result = parser.parseAddress("123 RUE PRINCIPALE\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
        }

        @Test
        @DisplayName("Regression: RUE PRINCIPALE O — French with direction")
        void regression_ruePrincipaleO() {
            var result = parser.parseAddress("123 RUE PRINCIPALE O\nMONTRÉAL QC H2X 1Y4");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().streetDirection()).isEqualTo("O");
        }

        @Test
        @DisplayName("Regression: ROUTE + number — QC convention unchanged")
        void regression_routeNumber() {
            var result = parser.parseAddress("781 ROUTE 465\nMONCTON NB E1C 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("465");
            assertThat(result.components().streetType()).isEqualTo("ROUTE");
        }

        @Test
        @DisplayName("Regression: normal AVE suffix — 2ND AVE N")
        void regression_normalAveSuffix() {
            var result = parser.parseAddress("123 2ND AVE N\nSASKATOON SK S7M 2L9");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("2ND");
            assertThat(result.components().streetType()).isEqualTo("AVE");
            assertThat(result.components().streetDirection()).isEqualTo("N");
        }

        @Test
        @DisplayName("Regression: MAIN ST N — English pattern unaffected")
        void regression_mainStN() {
            var result = parser.parseAddress("123 MAIN ST N\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
            assertThat(result.components().streetDirection()).isEqualTo("N");
        }
    }

    @Nested
    @DisplayName("Hyphen and Ampersand Preservation in Street Names")
    class HyphenAmpersandTests {

        @Test
        @DisplayName("Adjacent hyphenated ordinals preserved: 2E-ET-3E")
        void adjacentHyphenOrdinals() {
            var result = parser.parseAddress("JEAN DUPONT\n631 2E-ET-3E RANG DE COLOMBOURG\nQUÉBEC QC G1K 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("2E-ET-3E RANG DE COLOMBOURG");
        }

        @Test
        @DisplayName("Adjacent hyphenated ordinals: 8E-ET-9E RANG")
        void adjacentHyphenOrdinals8e9e() {
            var result = parser.parseAddress("JEAN DUPONT\n234 8E-ET-9E RANG\nQUÉBEC QC G1K 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("8E-ET-9E");
            assertThat(result.components().streetType()).isEqualTo("RANG");
        }

        @Test
        @DisplayName("Adjacent hyphenated rang: DU 6E-RANG CH")
        void adjacentHyphenRang() {
            var result = parser.parseAddress("JEAN DUPONT\n100 DU 6E-RANG CH\nQUÉBEC QC G1K 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("DU 6E-RANG");
            assertThat(result.components().streetType()).isEqualTo("CH");
        }

        @Test
        @DisplayName("Concession with hyphenated numbers: 27-28 SIDERD")
        void hyphenatedSideroad() {
            var result = parser.parseAddress("115418 27-28 SIDERD\nINNISFIL ON L9S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("27-28");
            assertThat(result.components().streetType()).isEqualTo("SIDERD");
        }

        @Test
        @DisplayName("Ampersand preserved: 6 & 10 SIDERD")
        void ampersandPreserved() {
            var result = parser.parseAddress("JOHN SMITH\n182 6 & 10 SIDERD\nINNISFIL ON L9S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("6 & 10");
            assertThat(result.components().streetType()).isEqualTo("SIDERD");
        }

        @Test
        @DisplayName("Ampersand in highway: HWY 11 & 17")
        void ampersandHighway() {
            var result = parser.parseAddress("JOHN SMITH\n100 HWY 11 & 17\nTHUNDER BAY ON P7B 1A1");
            assertThat(result.successful()).isTrue();
            // HWY is not at the end so it's part of the name — parser preserves as-is
            assertThat(result.components().streetName()).isEqualTo("HWY 11 & 17");
        }

        @Test
        @DisplayName("Slash preserved in compound sideroad: 36/37 NOTTAWASAGA SIDERD")
        void slashCompoundSideroad() {
            var result = parser.parseAddress("7722 36/37 NOTTAWASAGA SIDERD\nINNISFIL ON L9S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("36/37 NOTTAWASAGA");
            assertThat(result.components().streetType()).isEqualTo("SIDERD");
        }

        @Test
        @DisplayName("Slash preserved in bilingual name: PETIT/LITTLE DOVER CH")
        void slashBilingualName() {
            var result = parser.parseAddress("73 PETIT/LITTLE DOVER CH\nMONCTON NB E1C 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PETIT/LITTLE DOVER");
            assertThat(result.components().streetType()).isEqualTo("CH");
        }

        @Test
        @DisplayName("Slash preserved in highway: HIGHWAY 11/17")
        void slashHighway() {
            var result = parser.parseAddress("512 HIGHWAY 11/17\nTHUNDER BAY ON P7B 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("HIGHWAY 11/17");
        }

        @Test
        @DisplayName("Spaced dash preserved: DRUMMOND RD - RTE 113 (PE dual-name)")
        void spacedDashPE() {
            var result = parser.parseAddress("455 DRUMMOND RD - RTE 113\nMONTAGUE PE C0A 1R0");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("DRUMMOND RD - RTE 113");
        }

        @Test
        @DisplayName("Spaced dash preserved: TALLY HO - SWORDS RD (ON boundary)")
        void spacedDashON() {
            var result = parser.parseAddress("667 TALLY HO - SWORDS RD\nINNISFIL ON L9S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("TALLY HO - SWORDS");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        @Test
        @DisplayName("Spaced dash preserved: BRAS D'OR - FLORENCE RD (NS bilingual)")
        void spacedDashNS() {
            var result = parser.parseAddress("115 BRAS D'OR - FLORENCE RD\nBRAS D'OR NS B1Y 2K5");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("BRAS D'OR - FLORENCE");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        // ---- Opposing direction pair preservation ----

        @Test
        @DisplayName("EAST WEST RD — opposing pair kept in name, not consumed as direction")
        void opposingPairEastWest() {
            var result = parser.parseAddress("949 EAST WEST RD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("EAST WEST");
            assertThat(result.components().streetType()).isEqualTo("RD");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("NORTH SOUTH DR — opposing pair kept in name")
        void opposingPairNorthSouth() {
            var result = parser.parseAddress("100 NORTH SOUTH DR\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("NORTH SOUTH");
            assertThat(result.components().streetType()).isEqualTo("DR");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("EAST-WEST RD — hyphenated form already a compound word")
        void opposingPairHyphenated() {
            var result = parser.parseAddress("949 EAST-WEST RD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("EAST-WEST");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        @Test
        @DisplayName("EAST/WEST RD — slashed form already a compound word")
        void opposingPairSlashed() {
            var result = parser.parseAddress("949 EAST/WEST RD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("EAST/WEST");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        // ---- Mid-name direction: only short abbreviations ----

        @Test
        @DisplayName("THE WEST MALL — WEST is part of name, not mid-name direction")
        void theWestMall() {
            var result = parser.parseAddress("APT 1214 545 THE WEST MALL\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("THE WEST");
            assertThat(result.components().streetType()).isEqualTo("MALL");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("PARK EAST DR — EAST is part of name, not mid-name direction")
        void parkEastDr() {
            var result = parser.parseAddress("100 PARK EAST DR\nWINNIPEG MB R3T 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PARK EAST");
            assertThat(result.components().streetType()).isEqualTo("DR");
            assertThat(result.components().streetDirection()).isEmpty();
        }

        @Test
        @DisplayName("Regression: VICTORIA E ST — short form E still consumed as mid-name direction")
        void midNameShortFormStillWorks() {
            var result = parser.parseAddress("100 VICTORIA E ST\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("VICTORIA");
            assertThat(result.components().streetType()).isEqualTo("ST");
            assertThat(result.components().streetDirection()).isEqualTo("E");
        }

        @Test
        @DisplayName("Regression: MAIN ST N — suffix direction still works")
        void suffixDirectionStillWorks() {
            var result = parser.parseAddress("123 MAIN ST NORTH\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
            assertThat(result.components().streetDirection()).isEqualTo("NORTH");
        }

        @Test
        @DisplayName("Parentheses preserved: DRIVEWAY (THE)")
        void parenthesesPreserved() {
            var result = parser.parseAddress("APT 1003 20 DRIVEWAY (THE)\nOTTAWA ON K1S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("DRIVEWAY (THE)");
        }

        @Test
        @DisplayName("Parentheses preserved: PARKWAY (THE)")
        void parenthesesParkway() {
            var result = parser.parseAddress("55 PARKWAY (THE)\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PARKWAY (THE)");
        }

        @Test
        @DisplayName("Parenthetical civic number: (3195) SOME ST")
        void parentheticalCivicNumber() {
            var result = parser.parseAddress("(3195) MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetNumber()).isEqualTo("3195");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Adjacent hyphen NOT affected by spaced dash fix")
        void adjacentHyphenUnaffected() {
            var result = parser.parseAddress("631 2E-ET-3E RANG DE COLOMBOURG\nQUÉBEC QC G1K 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("2E-ET-3E RANG DE COLOMBOURG");
        }

        @Test
        @DisplayName("Regression: existing hyphenated names still work (CÔTE-DES-NEIGES)")
        void regressionHyphenatedFrenchName() {
            var result = parser.parseAddress("456 CHEMIN DE LA CÔTE-DES-NEIGES\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("CHEMIN");
            assertThat(result.components().streetName()).isEqualTo("DE LA CÔTE-DES-NEIGES");
        }

        @Test
        @DisplayName("Regression: unit-hyphen-civic still works (10-123)")
        void regressionUnitHyphenCivic() {
            var result = parser.parseAddress("10-123 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("10");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
        }

        @Test
        @DisplayName("Regression: fractional civic still works (123 1/2)")
        void regressionFractionalCivic() {
            var result = parser.parseAddress("123 1/2 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().streetNumber()).isEqualTo("123 1/2");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
        }
    }

    @Nested
    @DisplayName("NAR-Validated Street Type Abbreviations")
    class NarStreetTypeTests {

        @Test
        @DisplayName("CROIS recognized as Croissant (QC)")
        void croisRecognized() {
            var result = parser.parseAddress("231 CHAUMONT CROIS\nLAVAL QC H7N 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("CHAUMONT");
            assertThat(result.components().streetType()).isEqualTo("CROIS");
        }

        @Test
        @DisplayName("SIDERD recognized as Sideroad (ON)")
        void siderdRecognized() {
            var result = parser.parseAddress("1038 25 SIDERD\nINNISFIL ON L9S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("25");
            assertThat(result.components().streetType()).isEqualTo("SIDERD");
        }

        @Test
        @DisplayName("CONC recognized as Concession (ON)")
        void concRecognized() {
            var result = parser.parseAddress("1518 6 CONC\nINNISFIL ON L9S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("6");
            assertThat(result.components().streetType()).isEqualTo("CONC");
        }

        @Test
        @DisplayName("CIRCT recognized as Circuit (ON)")
        void circtRecognized() {
            var result = parser.parseAddress("APT 109 45 GREENBRAE CIRCT\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GREENBRAE");
            assertThat(result.components().streetType()).isEqualTo("CIRCT");
        }

        @Test
        @DisplayName("VILLGE recognized as Village (AB)")
        void villgeRecognized() {
            var result = parser.parseAddress("93 GRANDIN VILLGE\nST. ALBERT AB T8N 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("GRANDIN");
            assertThat(result.components().streetType()).isEqualTo("VILLGE");
        }

        @Test
        @DisplayName("RLE recognized as Ruelle (QC)")
        void rleRecognized() {
            var result = parser.parseAddress("125 DE L'ÉQUERRE RLE\nMONTRÉAL QC H2X 1Y4");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("RLE");
        }

        @Test
        @DisplayName("SENT recognized as Sentier (QC)")
        void sentRecognized() {
            var result = parser.parseAddress("418 DES FOUGÈRES SENT\nQUÉBEC QC G1K 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetType()).isEqualTo("SENT");
        }

        @Test
        @DisplayName("TLINE recognized as Townline (ON)")
        void tlineRecognized() {
            var result = parser.parseAddress("36 ASHFIELD-HURON TLINE\nGODERICH ON N7A 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("ASHFIELD-HURON");
            assertThat(result.components().streetType()).isEqualTo("TLINE");
        }

        @Test
        @DisplayName("CTR recognized as Centre (AB)")
        void ctrRecognized() {
            var result = parser.parseAddress("APT 124 122 MAHOGANY CTR SE\nCALGARY AB T3M 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("MAHOGANY");
            assertThat(result.components().streetType()).isEqualTo("CTR");
            assertThat(result.components().streetDirection()).isEqualTo("SE");
        }

        @Test
        @DisplayName("POINTE recognized (AB)")
        void pointeRecognized() {
            var result = parser.parseAddress("522 CALLAGHAN POINTE SW\nEDMONTON AB T6W 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("CALLAGHAN");
            assertThat(result.components().streetType()).isEqualTo("POINTE");
            assertThat(result.components().streetDirection()).isEqualTo("SW");
        }

        @Test
        @DisplayName("HGHLDS recognized as Highlands (SK)")
        void hghldsRecognized() {
            var result = parser.parseAddress("1071 WASCANA HGHLDS\nREGINA SK S4V 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("WASCANA");
            assertThat(result.components().streetType()).isEqualTo("HGHLDS");
        }

        @Test
        @DisplayName("PTWAY recognized as Pathway (ON)")
        void ptwayRecognized() {
            var result = parser.parseAddress("100 RIVERSIDE PTWAY\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("RIVERSIDE");
            assertThat(result.components().streetType()).isEqualTo("PTWAY");
        }

        @Test
        @DisplayName("DRWY recognized as Driveway (ON)")
        void drwyRecognized() {
            var result = parser.parseAddress("APT 101 364 QUEEN ELIZABETH DRWY\nOTTAWA ON K1S 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("QUEEN ELIZABETH");
            assertThat(result.components().streetType()).isEqualTo("DRWY");
        }

        @Test
        @DisplayName("HARBR recognized as Harbour (AB)")
        void harbrRecognized() {
            var result = parser.parseAddress("55 ROYAL HARBR\nCALGARY AB T3M 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("ROYAL");
            assertThat(result.components().streetType()).isEqualTo("HARBR");
        }
    }

    @Nested
    @DisplayName("French Type-as-Name Guard")
    class FrenchTypeAsNameGuardTests {

        @Test
        @DisplayName("AVENUE RD → name=AVENUE, type=RD (Toronto's Avenue Road)")
        void avenueRd() {
            var result = parser.parseAddress("APT 912 38 AVENUE RD\nTORONTO ON M5R 2G2");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("AVENUE");
            assertThat(result.components().streetType()).isEqualTo("RD");
        }

        @Test
        @DisplayName("BOULEVARD DR → name=BOULEVARD, type=DR")
        void boulevardDr() {
            var result = parser.parseAddress("620 BOULEVARD DR\nSASKATOON SK S7M 2L9");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("BOULEVARD");
            assertThat(result.components().streetType()).isEqualTo("DR");
        }

        @Test
        @DisplayName("PROMENADE DR → name=PROMENADE, type=DR")
        void promenadeDr() {
            var result = parser.parseAddress("137 PROMENADE DR\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PROMENADE");
            assertThat(result.components().streetType()).isEqualTo("DR");
        }

        @Test
        @DisplayName("COTE BLVD → name=COTE, type=BLVD")
        void coteBlvd() {
            var result = parser.parseAddress("888 COTE BLVD\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("COTE");
            assertThat(result.components().streetType()).isEqualTo("BLVD");
        }

        @Test
        @DisplayName("PROMENADE CIR → name=PROMENADE, type=CIR")
        void promenadeCir() {
            var result = parser.parseAddress("APT 1801 88 PROMENADE CIR\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PROMENADE");
            assertThat(result.components().streetType()).isEqualTo("CIR");
        }

        @Test
        @DisplayName("COTE AVE → name=COTE, type=AVE")
        void coteAve() {
            var result = parser.parseAddress("121 COTE AVE\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("COTE");
            assertThat(result.components().streetType()).isEqualTo("AVE");
        }

        @Test
        @DisplayName("JARDIN PVT → name=JARDIN, type=PVT")
        void jardinPvt() {
            var result = parser.parseAddress("40 JARDIN PVT\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("JARDIN");
            assertThat(result.components().streetType()).isEqualTo("PVT");
        }

        @Test
        @DisplayName("COTE AVE. — period on type doesn't prevent guard")
        void coteAvePeriod() {
            var result = parser.parseAddress("121 COTE AVE.\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("COTE");
            assertThat(result.components().streetType()).isEqualTo("AVE");
        }

        @Test
        @DisplayName("PROMENADE PL. — period on type doesn't prevent guard")
        void promenadePlPeriod() {
            var result = parser.parseAddress("50 PROMENADE PL.\nTORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PROMENADE");
            assertThat(result.components().streetType()).isEqualTo("PL");
        }

        @Test
        @DisplayName("Regression: ROUTE ACRES — ACRES is a full word, not in guard")
        void regression_routeAcres() {
            var result = parser.parseAddress("105 ROUTE ACRES\nMONTREAL QC H1H 1H1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("ACRES");
            assertThat(result.components().streetType()).isEqualTo("ROUTE");
        }

        @Test
        @DisplayName("Regression: AVENUE DU PARC unchanged (multi-word name)")
        void regression_avenueDuParc() {
            var result = parser.parseAddress("123 AVENUE DU PARC\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("DU PARC");
            assertThat(result.components().streetType()).isEqualTo("AVENUE");
        }

        @Test
        @DisplayName("Regression: RUE PRINCIPALE unchanged")
        void regression_ruePrincipale() {
            var result = parser.parseAddress("123 RUE PRINCIPALE\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
        }

        @Test
        @DisplayName("Regression: ROUTE 465 unchanged (QC convention)")
        void regression_route465() {
            var result = parser.parseAddress("781 ROUTE 465\nMONCTON NB E1C 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetName()).isEqualTo("465");
            assertThat(result.components().streetType()).isEqualTo("ROUTE");
        }
    }

    @Nested
    @DisplayName("Compound Unit Value Parsing")
    class CompoundUnitValueTests {

        @Test
        @DisplayName("Should parse fractional unit (1/2)")
        void shouldParseFractionalUnit() {
            var result = parser.parseAddress("APT 1/2 85 EDWARD ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("1/2");
            assertThat(result.components().streetNumber()).isEqualTo("85");
            assertThat(result.components().streetName()).isEqualTo("EDWARD");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should parse hyphenated unit (405-2)")
        void shouldParseHyphenatedUnit() {
            var result = parser.parseAddress("APT 405-2 123 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("405-2");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should parse dotted unit (1101.1)")
        void shouldParseDottedUnit() {
            var result = parser.parseAddress("APT 1101.1 229 LINDEN AVE\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("1101.1");
            assertThat(result.components().streetNumber()).isEqualTo("229");
            assertThat(result.components().streetName()).isEqualTo("LINDEN");
            assertThat(result.components().streetType()).isEqualTo("AVE");
        }

        @Test
        @DisplayName("Should treat compound with no following civic as street number")
        void shouldTreatCompoundNoFollowingCivicAsStreetNumber() {
            var result = parser.parseAddress("APT 1/2 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEmpty();
            assertThat(result.components().streetNumber()).isEqualTo("1/2");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
        }

        @Test
        @DisplayName("Regression: simple numeric unit still works")
        void regressionSimpleNumericUnit() {
            var result = parser.parseAddress("APT 5 394 QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("5");
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Regression: bare designator number is civic, not unit")
        void regressionBareDesignatorNumber() {
            var result = parser.parseAddress("APT 394 QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEmpty();
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Regression: fraction after civic number is street number, not unit")
        void regressionFractionAfterCivic() {
            var result = parser.parseAddress("123 1/2 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().streetNumber()).isEqualTo("123 1/2");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Regression: unit-hyphen-civic pattern still works")
        void regressionUnitHyphenCivic() {
            var result = parser.parseAddress("10-123 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("10");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }
    }

    @Nested
    @DisplayName("Prefix Unit Parsing")
    class PrefixUnitParsingTests {

        @Test
        @DisplayName("Should parse letter unit after designator")
        void shouldParseLetterUnit() {
            var result = parser.parseAddress("APT A 394 QUEEN ST\nCHARLOTTETOWN PE C1A 1A1");
            assertThat(result.components().unitNumber()).isEqualTo("A");
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should parse alphanumeric code unit (B15)")
        void shouldParseAlphanumericCodeUnit() {
            var result = parser.parseAddress("APT B15 20 DUVAR CRT\nCHARLOTTETOWN PE C1A 1A1");
            assertThat(result.components().unitNumber()).isEqualTo("B15");
            assertThat(result.components().streetNumber()).isEqualTo("20");
            assertThat(result.components().streetName()).isEqualTo("DUVAR");
            assertThat(result.components().streetType()).isEqualTo("CRT");
        }

        @Test
        @DisplayName("Should parse long code unit (K102)")
        void shouldParseLongCodeUnit() {
            var result = parser.parseAddress("APT K102 229 LINDEN AVE\nCHARLOTTETOWN PE C1A 1A1");
            assertThat(result.components().unitNumber()).isEqualTo("K102");
            assertThat(result.components().streetNumber()).isEqualTo("229");
            assertThat(result.components().streetName()).isEqualTo("LINDEN");
            assertThat(result.components().streetType()).isEqualTo("AVE");
        }

        @Test
        @DisplayName("Should parse digit-alpha unit (5A)")
        void shouldParseDigitAlphaUnit() {
            var result = parser.parseAddress("APT 5A 394 QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("5A");
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should parse numeric unit with civic number")
        void shouldParseNumericUnitWithCivic() {
            var result = parser.parseAddress("APT 5 394 QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("5");
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should treat bare designator number as civic, not unit")
        void shouldTreatBareDesignatorNumberAsCivic() {
            var result = parser.parseAddress("APT 394 QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEmpty();
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should handle bare designator with no civic number")
        void shouldHandleBareDesignatorNoCivic() {
            var result = parser.parseAddress("APT QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEmpty();
            assertThat(result.components().streetNumber()).isEmpty();
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should parse alphanum unit with no civic")
        void shouldParseAlphanumUnitNoCivic() {
            var result = parser.parseAddress("APT 5A QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("5A");
            assertThat(result.components().streetNumber()).isEmpty();
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should treat direction letter as unit when followed by civic")
        void shouldTreatDirectionLetterAsUnit() {
            var result = parser.parseAddress("APT E 394 QUEEN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("E");
            assertThat(result.components().streetNumber()).isEqualTo("394");
            assertThat(result.components().streetName()).isEqualTo("QUEEN");
            assertThat(result.components().streetType()).isEqualTo("ST");
        }

        @Test
        @DisplayName("Should parse French designator with letter unit")
        void shouldParseFrenchDesignatorLetterUnit() {
            var result = parser.parseAddress("APP A 123 RUE PRINCIPALE\nMONTRÉAL QC H3Z 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("A");
            assertThat(result.components().streetNumber()).isEqualTo("123");
        }

        @Test
        @DisplayName("Should parse UNIT designator with letter")
        void shouldParseUnitDesignatorLetter() {
            var result = parser.parseAddress("UNIT B 456 ELM AVE\nVANCOUVER BC V6B 2K1");
            assertThat(result.components().unitNumber()).isEqualTo("B");
            assertThat(result.components().streetNumber()).isEqualTo("456");
        }

        @Test
        @DisplayName("Should parse SUITE designator with numeric unit")
        void shouldParseSuiteDesignatorNumeric() {
            var result = parser.parseAddress("SUITE 200 123 MAIN ST\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("200");
            assertThat(result.components().streetNumber()).isEqualTo("123");
        }

        @Test
        @DisplayName("Should parse PH code unit")
        void shouldParsePhCodeUnit() {
            var result = parser.parseAddress("APT PH01 5 STRAWBERRY LANE\nTORONTO ON M5V 2Y7");
            assertThat(result.components().unitNumber()).isEqualTo("PH01");
            assertThat(result.components().streetNumber()).isEqualTo("5");
        }
    }
}
