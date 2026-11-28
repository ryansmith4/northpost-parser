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
import org.junit.jupiter.params.provider.CsvSource;
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

        // French convention puts a comma after the civic number: "123, rue Principale" (issue #66).

        @Test
        @DisplayName("French civic-number comma on a single line: 123, RUE PRINCIPALE, MONTRÉAL QC")
        void frenchCivicComma_singleLine() {
            var result = parser.parseAddress("123, RUE PRINCIPALE, MONTRÉAL QC H3B 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().municipality()).isEqualTo("MONTRÉAL");
            assertThat(result.components().province()).isEqualTo("QC");
            assertThat(result.components().postalCode()).isEqualTo("H3B 1A1");
        }

        @Test
        @DisplayName("French civic-number comma after a leading unit: APP 4, 123, RUE PRINCIPALE")
        void frenchCivicComma_leadingUnit() {
            var result = parser.parseAddress("APP 4, 123, RUE PRINCIPALE\nMONTRÉAL QC H3B 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("4");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().municipality()).isEqualTo("MONTRÉAL");
        }

        @Test
        @DisplayName("French civic-number comma with unit and region on a single line")
        void frenchCivicComma_leadingUnitSingleLine() {
            var result = parser.parseAddress("APP 4, 123, RUE PRINCIPALE, MONTRÉAL QC H3B 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("4");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().municipality()).isEqualTo("MONTRÉAL");
            assertThat(result.components().province()).isEqualTo("QC");
            assertThat(result.components().postalCode()).isEqualTo("H3B 1A1");
        }

        @Test
        @DisplayName("Unit-hyphen-civic with French comma on a single line: 4-123, RUE PRINCIPALE, MONTRÉAL QC")
        void frenchCivicComma_unitHyphenCivic() {
            var result = parser.parseAddress("4-123, RUE PRINCIPALE, MONTRÉAL QC H3B 1A1");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("4");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("PRINCIPALE");
            assertThat(result.components().streetType()).isEqualTo("RUE");
            assertThat(result.components().municipality()).isEqualTo("MONTRÉAL");
        }

        @Test
        @DisplayName("Regression: French civic-number comma on its own line with direction")
        void frenchCivicComma_multiLineWithDirection() {
            var result = parser.parseAddress("JEAN DUPONT\n1234, BOUL SAINT-LAURENT O\nMONTRÉAL QC H2X 2S9");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().addressee()).isEqualTo("JEAN DUPONT");
            assertThat(result.components().streetNumber()).isEqualTo("1234");
            assertThat(result.components().streetName()).isEqualTo("SAINT-LAURENT");
            assertThat(result.components().streetType()).isEqualTo("BOUL");
            assertThat(result.components().streetDirection()).isEqualTo("O");
        }

        @Test
        @DisplayName("Regression: English unit comma on a single line: SUITE 200, 123 MAIN ST, TORONTO ON")
        void englishUnitComma_singleLine() {
            var result = parser.parseAddress("SUITE 200, 123 MAIN ST, TORONTO ON M5V 2Y7");
            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("200");
            assertThat(result.components().streetNumber()).isEqualTo("123");
            assertThat(result.components().streetName()).isEqualTo("MAIN");
            assertThat(result.components().streetType()).isEqualTo("ST");
            assertThat(result.components().municipality()).isEqualTo("TORONTO");
            assertThat(result.components().postalCode()).isEqualTo("M5V 2Y7");
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
    @DisplayName("Region Line Province Detection")
    class RegionLineProvinceTests {

        @Test
        @DisplayName("Should keep a municipality containing a province code intact (NIAGARA ON THE LAKE)")
        void shouldKeepMunicipalityContainingProvinceCode() {
            var result = parser.parseAddress("JOHN SMITH\n1875 LAKESHORE RD\nNIAGARA ON THE LAKE ON L0S 1J0");

            assertThat(result.successful()).isTrue();
            assertThat(result.components().municipality()).isEqualTo("NIAGARA ON THE LAKE");
            assertThat(result.components().province()).isEqualTo("ON");
            assertThat(result.components().postalCode()).isEqualTo("L0S 1J0");
        }

        @Test
        @DisplayName("Should keep the municipality intact when the postal code has no space")
        void shouldKeepMunicipalityIntactWithUnspacedPostalCode() {
            var result = parser.parseAddress("JOHN SMITH\n6 SEYVAL PL\nNIAGARA ON THE LAKE ON  L0S1J0");

            assertThat(result.components().municipality()).isEqualTo("NIAGARA ON THE LAKE");
            assertThat(result.components().province()).isEqualTo("ON");
            assertThat(result.components().postalCode()).isEqualTo("L0S 1J0");
        }

        @Test
        @DisplayName("Should keep the municipality intact when the postal code is on its own line")
        void shouldKeepMunicipalityIntactWithPostalCodeOnOwnLine() {
            var result = parser.parseAddress("JOHN SMITH\n17 BROCK ST\nNIAGARA ON THE LAKE ON\nL0S 1J0");

            assertThat(result.components().municipality()).isEqualTo("NIAGARA ON THE LAKE");
            assertThat(result.components().province()).isEqualTo("ON");
            assertThat(result.components().postalCode()).isEqualTo("L0S 1J0");
        }

        @Test
        @DisplayName("Should prefer the province nearest the postal code when the municipality contains another code")
        void shouldPreferProvinceNearestPostalCodeForNonOntarioCode() {
            // "NB" inside the municipality must not be taken as New Brunswick
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nPORT NB HARBOUR NS B0J 1J0");

            assertThat(result.components().municipality()).isEqualTo("PORT NB HARBOUR");
            assertThat(result.components().province()).isEqualTo("NS");
            assertThat(result.components().postalCode()).isEqualTo("B0J 1J0");
        }

        @Test
        @DisplayName("Should keep the municipality intact when the province is written as a full name")
        void shouldKeepMunicipalityIntactWithFullProvinceName() {
            var result = parser.parseAddress("JOHN SMITH\n1875 LAKESHORE RD\nNIAGARA ON THE LAKE ONTARIO L0S 1J0");

            assertThat(result.components().municipality()).isEqualTo("NIAGARA ON THE LAKE");
            assertThat(result.components().province()).isEqualTo("ON");
            assertThat(result.components().postalCode()).isEqualTo("L0S 1J0");
        }

        @Test
        @DisplayName("Should keep the municipality intact in a comma-separated single-line address")
        void shouldKeepMunicipalityIntactInCommaSeparatedAddress() {
            var result = parser.parseAddress("1875 LAKESHORE RD, NIAGARA ON THE LAKE, ON L0S 1J0");

            assertThat(result.components().streetNumber()).isEqualTo("1875");
            assertThat(result.components().streetName()).isEqualTo("LAKESHORE");
            assertThat(result.components().municipality()).isEqualTo("NIAGARA ON THE LAKE");
            assertThat(result.components().province()).isEqualTo("ON");
            assertThat(result.components().postalCode()).isEqualTo("L0S 1J0");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON M5V 2Y7",
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON M5V 2Y7 CANADA",
                    "JOHN SMITH\n123 MAIN ST\nTORONTO ON"
                })
        @DisplayName("Should still find a province that appears only once")
        void shouldStillFindSingleProvince(String address) {
            var result = parser.parseAddress(address);

            assertThat(result.components().municipality()).isEqualTo("TORONTO");
            assertThat(result.components().province()).isEqualTo("ON");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC BC",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC BC\nV1M 2B3",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC BC CANADA",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC BC V1M 2B3",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC BC BC V1M 2B3",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY, BC, BC V1M 2B3",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC V1M 2B3 BC",
                    "JOHN SMITH\n20852 78B AVE\nLANGLEY BC BRITISH COLUMBIA V1M 2B3",
                    "20852 78B AVE, LANGLEY BC BC V1M 2B3"
                })
        @DisplayName("Should keep a repeated province code out of the municipality")
        void shouldKeepRepeatedProvinceCodeOutOfMunicipality(String address) {
            var result = parser.parseAddress(address);

            assertThat(result.components().municipality()).isEqualTo("LANGLEY");
            assertThat(result.components().province()).isEqualTo("BC");
        }

        @Test
        @DisplayName("Should leave the postal code and country alone when the province code is repeated")
        void shouldLeavePostalCodeAndCountryAloneWhenProvinceCodeIsRepeated() {
            var result = parser.parseAddress("JOHN SMITH\n20852 78B AVE\nLANGLEY BC BC V1M 2B3 CANADA");

            assertThat(result.components().streetNumber()).isEqualTo("20852");
            assertThat(result.components().municipality()).isEqualTo("LANGLEY");
            assertThat(result.components().province()).isEqualTo("BC");
            assertThat(result.components().postalCode()).isEqualTo("V1M 2B3");
            assertThat(result.components().country()).isEqualTo("CANADA");
        }

        @Test
        @DisplayName("Should drop a province code that is followed by the province name")
        void shouldDropProvinceCodeFollowedByProvinceName() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nTORONTO ON ONTARIO");

            assertThat(result.components().municipality()).isEqualTo("TORONTO");
            assertThat(result.components().province()).isEqualTo("ON");
        }

        @Test
        @DisplayName("Should leave the municipality empty when the line holds only a repeated province code")
        void shouldLeaveMunicipalityEmptyWhenLineHoldsOnlyRepeatedProvinceCode() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nBC BC V1M 2B3");

            assertThat(result.components().municipality()).isEmpty();
            assertThat(result.components().province()).isEqualTo("BC");
            assertThat(result.components().postalCode()).isEqualTo("V1M 2B3");
        }

        @Test
        @DisplayName("Known limit: a hyphen between the copies means the earlier one is not taken as a repeat")
        void shouldNotTakeCopyBeforeHyphenAsRepeat() {
            var result = parser.parseAddress("JOHN SMITH\n20852 78B AVE\nLANGLEY BC - BC");

            assertThat(result.components().municipality()).isEqualTo("LANGLEY BC -");
            assertThat(result.components().province()).isEqualTo("BC");
        }

        @Test
        @DisplayName("Known limit: a country between the copies means the earlier one is not taken as a repeat")
        void shouldNotTakeCopyBeforeCountryAsRepeat() {
            var result = parser.parseAddress("JOHN SMITH\n20852 78B AVE\nLANGLEY BC CANADA BC");

            assertThat(result.components().municipality()).isEqualTo("LANGLEY BC");
            assertThat(result.components().province()).isEqualTo("BC");
            assertThat(result.components().country()).isEqualTo("CANADA");
        }

        @Test
        @DisplayName("Should keep the municipality intact when its province code is repeated after it")
        void shouldKeepMunicipalityContainingProvinceCodeWhenCodeIsRepeated() {
            var result = parser.parseAddress("JOHN SMITH\n1875 LAKESHORE RD\nNIAGARA ON THE LAKE ON ON L0S 1J0");

            assertThat(result.components().municipality()).isEqualTo("NIAGARA ON THE LAKE");
            assertThat(result.components().province()).isEqualTo("ON");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "JEAN TREMBLAY\n12 RUE SAINT-JEAN\nQUEBEC QC G1R 1P8",
                    "JEAN TREMBLAY\n12 RUE SAINT-JEAN\nQUEBEC QC QC G1R 1P8",
                    "JEAN TREMBLAY\n12 RUE SAINT-JEAN\nQUEBEC, QC G1R 1P8"
                })
        @DisplayName("Should not take a municipality named like its province as a repeat of the province")
        void shouldNotTakeMunicipalityNamedLikeProvinceAsRepeat(String address) {
            var result = parser.parseAddress(address);

            assertThat(result.components().municipality()).isEqualTo("QUEBEC");
            assertThat(result.components().province()).isEqualTo("QC");
        }

        @Test
        @DisplayName("Should not merge two different province codes written in a row")
        void shouldNotMergeDifferentProvinceCodes() {
            var result = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nPORT NB NS B0J 1J0");

            assertThat(result.components().municipality()).isEqualTo("PORT NB");
            assertThat(result.components().province()).isEqualTo("NS");
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

    /**
     * A unit written on a line of its own ("APT 4"). The field values for the many layouts are checked by
     * {@link LayoutAddressTest}; the cases here cover what that file cannot express (the address type, strict mode) and
     * the limits of the rule.
     */
    @Nested
    @DisplayName("Unit On Its Own Line")
    class UnitOnOwnLineTests {

        private static final String CITY_LINE = "\nTORONTO ON M5V 2Y7";

        @Test
        @DisplayName("Unit line after the street keeps the street number and the CIVIC type")
        void shouldKeepStreetNumberWhenUnitLineFollows() {
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nAPT 4" + CITY_LINE)
                    .components();
            assertThat(c.unitNumber()).isEqualTo("4");
            assertThat(c.streetNumber()).isEqualTo("123");
            assertThat(c.streetName()).isEqualTo("MAIN");
            assertThat(c.addressType()).isEqualTo(AddressType.CIVIC);
            assertThat(c.isComplete()).isTrue();
        }

        @Test
        @DisplayName("Strict mode accepts an address with a unit line and reads it the same way")
        void shouldReadUnitLineInStrictMode() {
            var result = parser.parseAddress(
                    "JOHN SMITH\n123 MAIN ST\nAPT 4" + CITY_LINE, AddressParserService.ParsingStrategy.STRICT);
            assertThat(result.successful()).isTrue();
            assertThat(result.components().unitNumber()).isEqualTo("4");
            assertThat(result.components().streetNumber()).isEqualTo("123");
        }

        @Test
        @DisplayName("Unit line after a PO box does not turn the address into a civic one")
        void shouldKeepPostalBoxTypeWhenUnitLineFollows() {
            var c = parser.parseAddress("JOHN SMITH\nPO BOX 12\nSUITE 200" + CITY_LINE)
                    .components();
            assertThat(c.addressType()).isEqualTo(AddressType.POSTAL_BOX);
            assertThat(c.postalBoxNumber()).isEqualTo("12");
            assertThat(c.unitNumber()).isEqualTo("200");
            assertThat(c.streetNumber()).isEmpty();
        }

        @Test
        @DisplayName("Unit line after a rural route does not turn the address into a civic one")
        void shouldKeepRuralRouteTypeWhenUnitLineFollows() {
            var c = parser.parseAddress("JOHN SMITH\nRR 2\nUNIT 5\nSTONY PLAIN AB T7Z 1X1")
                    .components();
            assertThat(c.addressType()).isEqualTo(AddressType.RURAL_ROUTE);
            assertThat(c.ruralRoute()).isEqualTo("2");
            assertThat(c.unitNumber()).isEqualTo("5");
            assertThat(c.streetNumber()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"APT 4", "APT A", "UNIT 4B"})
        @DisplayName("A unit line with no street line gives a unit and an incomplete address, rejected by strict mode")
        void shouldNotInventStreetFromUnitLineAlone(String unitLine) {
            String address = "JOHN SMITH\n" + unitLine + CITY_LINE;

            var c = parser.parseAddress(address).components();
            assertThat(c.unitNumber()).isEqualTo(unitLine.substring(unitLine.indexOf(' ') + 1));
            assertThat(c.streetNumber()).isEmpty();
            assertThat(c.streetName()).isEmpty();
            assertThat(c.addressType()).isEqualTo(AddressType.INCOMPLETE);
            assertThat(c.isComplete()).isFalse();

            var strict = parser.parseAddress(address, AddressParserService.ParsingStrategy.STRICT);
            assertThat(strict.successful()).isFalse();
            assertThat(strict.errors()).contains("Missing delivery address");
        }

        /**
         * Each row is a street line that carries its own unit, with the fields it gives when it is the only delivery
         * line. Adding a separate unit line, before or after, must give exactly the same address. The rows ending in
         * {@code SUITE 9} are read oddly even alone (the second unit joins the street name); they are here because each
         * takes a different path through the parser, and a unit line must not change what that path does.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "5-123 MAIN ST                 | 5     | 123 | MAIN            | ST |",
                    "APT 5 123 MAIN ST             | 5     | 123 | MAIN            | ST |",
                    "APT5 123 MAIN ST              | 5     | 123 | MAIN            | ST |",
                    "123 MAIN ST APT 7             | 7     | 123 | MAIN            | ST |",
                    "100 KING ST W SUITE 500       | 500   | 100 | KING            | ST | W",
                    "5-123 MAIN ST SUITE 9         | 5     | 123 | MAIN ST SUITE 9 |    |",
                    "APT 5 123 MAIN ST SUITE 9     | 5     | 123 | MAIN ST SUITE 9 |    |",
                    "APT5 123 MAIN ST SUITE 9      | 5     | 123 | MAIN ST SUITE 9 |    |",
                    "APT 5A 123 MAIN ST SUITE 9    | 5A    | 123 | MAIN ST SUITE 9 |    |",
                    "APT A 123 MAIN ST SUITE 9     | A     | 123 | MAIN ST SUITE 9 |    |",
                    "APT 405-2 123 MAIN ST SUITE 9 | 405-2 | 123 | MAIN ST SUITE 9 |    |"
                })
        @DisplayName("A separate unit line changes nothing when the street line has its own unit")
        void shouldIgnoreUnitLineWhenStreetLineHasUnit(
                String streetLine, String unit, String number, String name, String type, String direction) {
            var alone =
                    parser.parseAddress("JOHN SMITH\n" + streetLine + CITY_LINE).components();
            assertThat(alone.unitNumber()).isEqualTo(unit);
            assertThat(alone.streetNumber()).isEqualTo(number);
            assertThat(alone.streetName()).isEqualTo(name);
            assertThat(alone.streetType()).isEqualTo(type == null ? "" : type);
            assertThat(alone.streetDirection()).isEqualTo(direction == null ? "" : direction);

            for (String unitLine : List.of("APT 4", "APT4", "FLOOR 2", "SUITE B", "UNIT 4B", "APT 405-2", "APT 4,")) {
                var unitFirst = parser.parseAddress("JOHN SMITH\n" + unitLine + "\n" + streetLine + CITY_LINE)
                        .components();
                var unitLast = parser.parseAddress("JOHN SMITH\n" + streetLine + "\n" + unitLine + CITY_LINE)
                        .components();
                assertThat(unitFirst).as("%s before %s", unitLine, streetLine).isEqualTo(alone);
                assertThat(unitLast).as("%s after %s", unitLine, streetLine).isEqualTo(alone);
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "TOWER RD",
                    "OFFICE PARK",
                    "UNIT RD",
                    "STE ANNE",
                    "APARTMENT THERAPY",
                    "SUITE DREAMS",
                    "APT STH",
                    "APT REARX",
                    "OFFICE TOWER",
                    "TOWER HILL RD",
                    "UNIT THE",
                    "APT MAINE",
                    "TOWER MAIN",
                    "APT SIDEN",
                    "APT MAINW",
                    "APT"
                })
        @DisplayName(
                "A unit-word line that is neither a unit nor a street leaves the street alone and is kept as care-of")
        void shouldSetAsideUnitWordLineThatIsNotAUnit(String line) {
            for (String street : List.of("123 MAIN ST", "123A MAIN ST", "(3195) MAIN ST")) {
                var alone =
                        parser.parseAddress("JOHN SMITH\n" + street + CITY_LINE).components();
                assertThat(alone.streetName()).isEqualTo("MAIN");
                assertThat(alone.streetNumber()).isNotEmpty();

                var after = parser.parseAddress("JOHN SMITH\n" + street + "\n" + line + CITY_LINE)
                        .components();
                var before = parser.parseAddress("JOHN SMITH\n" + line + "\n" + street + CITY_LINE)
                        .components();
                // No name line: the street line is the first line of the address
                var noName =
                        parser.parseAddress(street + "\n" + line + CITY_LINE).components();

                for (var c : List.of(after, before, noName)) {
                    assertThat(c.unitNumber())
                            .as("unit, %s with %s", line, street)
                            .isEmpty();
                    assertThat(c.streetNumber())
                            .as("number, %s with %s", line, street)
                            .isEqualTo(alone.streetNumber());
                    assertThat(c.streetName())
                            .as("name, %s with %s", line, street)
                            .isEqualTo("MAIN");
                    assertThat(c.streetType())
                            .as("type, %s with %s", line, street)
                            .isEqualTo("ST");
                    // The kept text is rebuilt from the tokens, so "416-555-1234" comes back with spaces
                    assertThat(c.careOf().replace(" ", ""))
                            .as("care-of, %s with %s", line, street)
                            .isEqualTo(line.replace(" ", ""));
                    assertThat(c.municipality()).isEqualTo("TORONTO");
                }
            }
        }

        @Test
        @DisplayName("A line set aside does not replace a care-of line that is already there")
        void shouldNotReplaceCareOfWithLineSetAside() {
            var c = parser.parseAddress("JOHN SMITH\nC/O JANE DOE\n123 MAIN ST\nTOWER RD" + CITY_LINE)
                    .components();
            assertThat(c.careOf()).isEqualTo("JANE DOE");
            assertThat(c.streetName()).isEqualTo("MAIN");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "STE CATHERINE ST W      | 3M  | CATHERINE  | ST  |",
                    "TOWER HILL RD           | 3M  | HILL       | RD  |",
                    "APT 394 QUEEN ST        | 394 | QUEEN      | ST  |",
                    "SUITE 200 300 TARTAN DR | 300 | TARTAN     | DR  | 200",
                    "APT 5 123 BROADWAY      | 123 | BROADWAY   |     | 5",
                    "APP 5 RUE PRINCIPALE    | 5   | PRINCIPALE | RUE |"
                })
        @DisplayName("A street line that starts with a unit word is read as a street even beside a numbered line")
        void shouldReadStreetThatStartsWithUnitWord(
                String streetLine, String number, String name, String type, String unit) {
            // "3M CANADA" starts with a number, so the address has a numbered line that is not the street. Where
            // the street line has no number, "3M" stays as the street number, as it did before this rule.
            var c = parser.parseAddress("3M CANADA\n" + streetLine + "\nLONDON ON N5V 4M9")
                    .components();
            assertThat(c.streetNumber()).isEqualTo(number == null ? "" : number);
            assertThat(c.streetName()).isEqualTo(name);
            assertThat(c.streetType()).isEqualTo(type == null ? "" : type);
            assertThat(c.unitNumber()).isEqualTo(unit == null ? "" : unit);
            assertThat(c.careOf()).isEmpty();
        }

        @ParameterizedTest(name = "{0} under {1}")
        @CsvSource(
                delimiter = '|',
                value = {
                    // A name with no street-type word is not taken for the street line
                    "APT A 103 HIGHROAD SOUTH | 1234567 ONTARIO INC               | 103 | HIGHROAD      |    | SOUTH | A",
                    "UNIT B 2200 KINGSWAY     | 3M CANADA                         | 2200 | KINGSWAY     |    |       | B",
                    "UNIT 4 250 KING ST W     | 3M CANADA                         | 250 | KING          | ST | W     | 4",
                    "SUITE 300 THE QUEENSWAY  | 3M CANADA                         | 300 | THE QUEENSWAY |    |       |",
                    "APT 394 QUEEN ST         | 1234567 ONTARIO INC               | 394 | QUEEN         | ST |       |",
                    "APT 5 123 BROADWAY       | 9123-4567 QUÉBEC INC              | 123 | BROADWAY      |    |       | 5",
                    // Neither is a name that starts with an ordinal, whatever words follow
                    "UNIT 4 250 KING ST W     | 1ST CHOICE AUTO CENTRE            | 250 | KING          | ST | W     | 4",
                    "APT 394 QUEEN ST         | 1ST CHOICE AUTO CENTRE            | 394 | QUEEN         | ST |       |",
                    // A name with a number and a street-type word (CENTRE, MR) passes for a street line, but a
                    // line that gives a unit and then a street number is the street all the same
                    "UNIT 4 250 KING ST W     | 24 HOUR PET CENTRE                | 250 | KING          | ST | W     | 4",
                    "SUITE 200 1 MAIN ST      | 4 CORNERS HEALTH CENTRE           | 1   | MAIN          | ST |       | 200",
                    "UNIT 4 250 KING ST W     | 1234567 ONTARIO INC ATTN MR SMITH | 250 | KING          | ST | W     | 4",
                    "UNIT 405-2 250 KING ST W | 24 HOUR PET CENTRE                | 250 | KING          | ST | W     | 405-2",
                    "UNIT B 2200 OAK AVE      | 24 HOUR PET CENTRE                | 2200 | OAK          | AVE |      | B"
                })
        @DisplayName("A name that starts with a number is not taken for the street when the street line follows")
        void shouldReadStreetLineUnderNameThatStartsWithDigit(
                String streetLine,
                String nameLine,
                String number,
                String name,
                String type,
                String direction,
                String unit) {
            String region = "\nLONDON ON N5V 4M9";
            for (String address : List.of(
                    nameLine + "\n" + streetLine + region, "JOHN SMITH\n" + nameLine + "\n" + streetLine + region)) {
                var c = parser.parseAddress(address).components();
                assertThat(c.streetNumber()).as("number, %s", address).isEqualTo(number);
                assertThat(c.streetName()).as("name, %s", address).isEqualTo(name);
                assertThat(c.streetType()).as("type, %s", address).isEqualTo(type == null ? "" : type);
                assertThat(c.streetDirection())
                        .as("direction, %s", address)
                        .isEqualTo(direction == null ? "" : direction);
                assertThat(c.unitNumber()).as("unit, %s", address).isEqualTo(unit == null ? "" : unit);
                assertThat(c.careOf()).as("care-of, %s", address).isEmpty();
            }
        }

        @ParameterizedTest(name = "{0} then {1}")
        @CsvSource(
                delimiter = '|',
                value = {
                    // A street number that ends in E is a street number: the line is clearly the street
                    "20E MAPLE CRT NW       | APT 3 BSMT                      | 20E | MAPLE      | CRT | NW | 3 |",
                    "20E MAPLE CRT NW       | SUITE 3400 FIRST CANADIAN PLACE | 20E | MAPLE      | CRT | NW | 3400 |",
                    "20E MAPLE CRT NW       | TOWER RD                        | 20E | MAPLE      | CRT | NW |   | TOWER RD",
                    // and after a unit it makes the line a street line, with any letter
                    "24 HOUR PET CENTRE     | UNIT 4 250E KING ST W           | 250E | KING      | ST  | W  | 4 |",
                    "24 HOUR PET CENTRE     | UNIT 4 250A KING ST W           | 250A | KING      | ST  | W  | 4 |",
                    "123 MAIN ST            | UNIT 4 250E KING ST W           | 250E | KING      | ST  | W  | 4 |",
                    // A French ordinal is not a street number, at the start of a line or after a unit
                    "1ER CHOIX AUTO CENTRE  | APP 394 RUE QUEEN               | 394 | QUEEN      | RUE |    |   |",
                    "3E ÉTAGE PLACE DU MARCHE | BUREAU 394 RUE QUEEN          | 394 | QUEEN      | RUE |    |   |",
                    "123 RUE PRINCIPALE     | APP 5 3E ÉTAGE ARRIÈRE          | 123 | PRINCIPALE | RUE |    | 5 |",
                    "123 RUE PRINCIPALE     | APP 5 12E AVENUE                | 123 | PRINCIPALE | RUE |    | 5 |"
                })
        @DisplayName("A number with the letter E is a street number unless a floor word or a street type follows it")
        void shouldTellStreetNumberEndingInEFromFrenchOrdinal(
                String first,
                String second,
                String number,
                String name,
                String type,
                String direction,
                String unit,
                String careOf) {
            var c = parser.parseAddress("JEAN TREMBLAY\n" + first + "\n" + second + "\nMONTRÉAL QC H3B 1A1")
                    .components();
            assertThat(c.streetNumber()).isEqualTo(number);
            assertThat(c.streetName()).isEqualTo(name);
            assertThat(c.streetType()).isEqualTo(type);
            assertThat(c.streetDirection()).isEqualTo(direction == null ? "" : direction);
            assertThat(c.unitNumber()).isEqualTo(unit == null ? "" : unit);
            assertThat(c.careOf()).isEqualTo(careOf == null ? "" : careOf);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "RM 204 3 WEST WING       | 204 | 3   | WEST WING",
                    "PH 416 555 1234 EXT 5    | PH416 | 555 | 1234 EXT 5",
                    "UNIT 12 24 HOUR ACCESS   | 12  | 24  | HOUR ACCESS"
                })
        @DisplayName("Known limit: a unit-word line with two numbers and a word replaces the street, as before")
        void shouldStillReadTwoNumbersAndWordAsStreetLine(String line, String unit, String number, String name) {
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\n" + line + CITY_LINE)
                    .components();
            assertThat(c.unitNumber()).isEqualTo(unit);
            assertThat(c.streetNumber()).isEqualTo(number);
            assertThat(c.streetName()).isEqualTo(name);
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNIT 4-250 KING ST W", "UNIT 4 - 250 KING ST W", "UNIT 12 B 250 KING ST W"})
        @DisplayName(
                "Known limit: a street number joined to the unit, or after a two-part unit, does not make a street line")
        void shouldStillSetAsideLineWhoseStreetNumberIsNotSetOff(String line) {
            // Under a business name that reads like a street, the line gives only its unit
            var c = parser.parseAddress("24 HOUR PET CENTRE\n" + line + "\nKITCHENER ON N2G 1B6")
                    .components();
            assertThat(c.streetNumber()).isEqualTo("24");
            assertThat(c.streetName()).isEqualTo("HOUR PET");
            assertThat(c.unitNumber()).isNotEmpty();
        }

        @Test
        @DisplayName("Known limit: on one line, what follows a unit and is not a delivery line joins the municipality")
        void shouldStillPutNonDeliverySegmentAfterUnitInMunicipality() {
            var c = parser.parseAddress("2ND FLOOR, MAIN ST, TORONTO ON M5V 2Y7")
                    .components();
            assertThat(c.unitNumber()).isEqualTo("2");
            assertThat(c.streetName()).isEmpty();
            assertThat(c.municipality()).isEqualTo("MAIN ST TORONTO");
            assertThat(c.province()).isEqualTo("ON");
            assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
        }

        @Test
        @DisplayName("Known limit: a numbered unit and the region alone on one line land in the addressee, as before")
        void shouldStillLoseRegionAfterNumberedUnitOnSingleLine() {
            var c = parser.parseAddress("APT 4, TORONTO ON M5V 2Y7").components();
            assertThat(c.addressee()).isEqualTo("APT 4 , TORONTO ON M5V 2Y7");
            assertThat(c.province()).isEmpty();
            assertThat(c.postalCode()).isEmpty();
        }

        @Test
        @DisplayName("A floor line with a place name after it is not taken for the street")
        void shouldReadStreetLineUnderFloorLineWithStreetTypeWord() {
            var c = parser.parseAddress("JEAN TREMBLAY\n2ND FLOOR PLACE DU MARCHE\nBUREAU 5 800 RUE PRINCIPALE O\n"
                            + "MONTRÉAL QC H3B 1A1")
                    .components();
            assertThat(c.streetNumber()).isEqualTo("800");
            assertThat(c.streetName()).isEqualTo("PRINCIPALE");
            assertThat(c.streetType()).isEqualTo("RUE");
            assertThat(c.streetDirection()).isEqualTo("O");
            assertThat(c.unitNumber()).isEqualTo("5");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "UNIT 4 250 KING ST W       | 250 | KING       | ST  | W | 4",
                    "UNIT 405-2 250 KING ST W   | 250 | KING       | ST  | W | 405-2",
                    "UNIT B 2200 KINGSWAY       | 2200 | KINGSWAY  |     |   | B",
                    "APP 4, 250, RUE PRINCIPALE | 250 | PRINCIPALE | RUE |   | 4"
                })
        @DisplayName(
                "Two street lines: one that gives a unit and then a street number is read, and the later line wins")
        void shouldReadSecondStreetLineThatHasUnitAndNumber(
                String secondLine, String number, String name, String type, String direction, String unit) {
            // Which of two streets is meant cannot be told, so the result is what it was before unit lines were
            // handled: the second line's street
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\n" + secondLine + CITY_LINE)
                    .components();
            assertThat(c.streetNumber()).isEqualTo(number);
            assertThat(c.streetName()).isEqualTo(name);
            assertThat(c.unitNumber()).isEqualTo(unit);
            assertThat(c.careOf()).isEmpty();
            if (type != null) {
                // With no type on the second line, the first line's type is left behind, as before
                assertThat(c.streetType()).isEqualTo(type);
                assertThat(c.streetDirection()).isEqualTo(direction == null ? "" : direction);
            }
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "APT 5 12             | 5     |",
                    "APT 5 3RD FLOOR REAR | 5     |",
                    "APT 405-2 QUEEN ST   | 405-2 |",
                    "APT 394 QUEEN ST     | 394   |",
                    "BLDG , 55 QUEEN ST E |       | BLDG , 55 QUEEN ST E",
                    "PH - 55 QUEEN ST E   |       | PH - 55 QUEEN ST E"
                })
        @DisplayName(
                "Beside a clear street line, a unit-word line without a unit and then a street number is set aside")
        void shouldSetAsideUnitWordLineWithoutStreetNumberOfItsOwn(String line, String unit, String careOf) {
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\n" + line + CITY_LINE)
                    .components();
            assertThat(c.streetNumber()).isEqualTo("123");
            assertThat(c.streetName()).isEqualTo("MAIN");
            assertThat(c.streetType()).isEqualTo("ST");
            assertThat(c.streetDirection()).isEmpty();
            assertThat(c.unitNumber()).isEqualTo(unit == null ? "" : unit);
            assertThat(c.careOf()).isEqualTo(careOf == null ? "" : careOf);
        }

        @Test
        @DisplayName("A line set aside keeps the street line's own unit, and the street type may come first or last")
        void shouldSetAsideMixedLineBesideStreetLineWithUnitOrFrenchOrder() {
            var withUnit = parser.parseAddress("JOHN SMITH\n123 MAIN ST APT 7\nOFFICE PARK" + CITY_LINE)
                    .components();
            assertThat(withUnit.streetName()).isEqualTo("MAIN");
            assertThat(withUnit.streetType()).isEqualTo("ST");
            assertThat(withUnit.unitNumber()).isEqualTo("7");
            assertThat(withUnit.careOf()).isEqualTo("OFFICE PARK");

            var french = parser.parseAddress("JEAN TREMBLAY\n123 RUE PRINCIPALE\nAPP 3 BSMT\nMONTRÉAL QC H3B 1A1")
                    .components();
            assertThat(french.streetNumber()).isEqualTo("123");
            assertThat(french.streetName()).isEqualTo("PRINCIPALE");
            assertThat(french.streetType()).isEqualTo("RUE");
            assertThat(french.unitNumber()).isEqualTo("3");
        }

        @Test
        @DisplayName(
                "Known limit: under a business name that reads like a street, a unit word and one number is a unit")
        void shouldStillTakeBusinessNameForStreetAboveUnitWordAndOneNumber() {
            // "24 HOUR PET CENTRE" starts with a number and has a street-type word, like "100 KING ST W". The line
            // below has one number, like "SUITE 3400 FIRST CANADIAN PLACE", so it gives a unit and no street.
            var c = parser.parseAddress("24 HOUR PET CENTRE\nAPT 394 QUEEN ST\nLONDON ON N5V 4M9")
                    .components();
            assertThat(c.unitNumber()).isEqualTo("394");
            assertThat(c.streetNumber()).isEqualTo("24");
            assertThat(c.streetName()).isEqualTo("HOUR PET");
            assertThat(c.streetType()).isEqualTo("CENTRE");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    // A unit word is not part of the unit
                    "APT 12 123 MAIN ST          | 12",
                    "APT12 123 MAIN ST           | 12",
                    "SUITE 12 123 MAIN ST        | 12",
                    "123 MAIN ST UNIT 12         | 12",
                    // A penthouse word is: penthouse 12 is not unit 12. The register writes PH04.
                    "PH 12 123 MAIN ST           | PH12",
                    "PH12 123 MAIN ST            | PH12",
                    "PH. 12 123 MAIN ST          | PH12",
                    "PH A 123 MAIN ST            | PHA",
                    "PH 04 123 MAIN ST           | PH04",
                    "PENTHOUSE 12 123 MAIN ST    | PH12",
                    "123 MAIN ST PH 12           | PH12",
                    "123 MAIN ST PENTHOUSE 2     | PH2",
                    // PH written in the value already is not doubled
                    "APT PH04 123 MAIN ST        | PH04",
                    "PENTHOUSE PH2 123 MAIN ST   | PH2",
                    // A unit word in front of another only introduces it, before or after the street
                    "123 MAIN ST UNIT PH 4       | PH4",
                    "123 MAIN ST UNIT PH. 4      | PH4",
                    "123 MAIN ST UNIT PH 4B      | PH4B",
                    "123 MAIN ST UNIT PH A       | PHA",
                    "123 MAIN ST APT PENTHOUSE 4 | PH4",
                    "123 MAIN ST APT FLOOR 2     | 2",
                    "UNIT PH 4 123 MAIN ST       | PH4",
                    "UNIT PH 4, 123 MAIN ST      | PH4",
                    "UNIT PH A 123 MAIN ST       | PHA",
                    "UNIT PH 4-2 123 MAIN ST     | PH4-2",
                    "APT FLOOR 2 123 MAIN ST     | 2",
                    // A unit word that is the whole value stays the value
                    "APT PH 123 MAIN ST          | PH",
                    "123 MAIN ST SUITE PH        | PH",
                    "123 MAIN ST APT TOWER       | TOWER",
                    // A value of two parts is not a unit word and its value
                    "123 MAIN ST APT 12 B        | 12 B",
                    "123 MAIN ST UNIT BSMT 2     | BSMT 2",
                    // The length of the value does not matter, joined or spaced
                    "APT1234567 123 MAIN ST      | 1234567",
                    "APT 1234567 123 MAIN ST     | 1234567"
                })
        @DisplayName("On the street line a penthouse word stays in the unit and any other unit word does not")
        void shouldKeepPenthouseWordInUnitOnStreetLine(String line, String unit) {
            var c = parser.parseAddress("JOHN SMITH\n" + line + CITY_LINE).components();
            assertThat(c.unitNumber()).isEqualTo(unit);
            assertThat(c.streetNumber()).isEqualTo("123");
            assertThat(c.streetName()).isEqualTo("MAIN");
            assertThat(c.streetType()).isEqualTo("ST");
            assertThat(c.careOf()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "APT PH 123 4 AVE SW      | PH | 123  | 4             | AVE",
                    "APT PH 123 4TH AVE SW    | PH | 123  | 4TH           | AVE",
                    "APT PH 4 123             | PH | 4    | 123           | ''",
                    "APT PH 123 1ST NATIONS RD | PH | 123 | 1ST NATIONS   | RD",
                    "APT PH 123 1/2 MAIN ST   | PH | 123 1/2 | MAIN       | ST",
                    "APT BSMT 123 100 MILE RD | BSMT | 123 | 100 MILE     | RD",
                    "UNIT PH UPPER 123 MAIN ST | '' | ''  | PH UPPER 123 MAIN | ST",
                    "UNIT PH 4 123-125 MAIN ST | PH | 4   | 123-125 MAIN  | ST",
                    "UNIT PH 4 123 RUE DUPONT | PH | 4    | 123 RUE DUPONT | ''"
                })
        @DisplayName("Two unit words at the start of a street line are read as before unless a value, a street "
                + "number and a street name follow")
        void shouldKeepUnitWordAsUnitBeforeNumberedStreet(
                String line, String unit, String number, String name, String type) {
            // The register writes the first rows as unit PH at 123 4 AVE SW. The last two rows are known limits:
            // they mean penthouse 4 at 123-125 MAIN ST and at 123 RUE DUPONT, and are read as they were before.
            var c = parser.parseAddress("JOHN SMITH\n" + line + "\nCALGARY AB T2P 1J9")
                    .components();
            assertThat(c.unitNumber()).isEqualTo(unit);
            assertThat(c.streetNumber()).isEqualTo(number);
            assertThat(c.streetName()).isEqualTo(name);
            assertThat(c.streetType()).isEqualTo(type);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "PH 12          | PH12",
                    "PH12           | PH12",
                    "PH. 12         | PH12",
                    "PENTHOUSE 12   | PH12",
                    "PH A           | PHA",
                    "APT PH 2       | PH2",
                    "UNIT PH        | PH",
                    "APT PH04       | PH04",
                    "PH 12, FLOOR 9 | PH12",
                    "APT 12         | 12"
                })
        @DisplayName("On a line of its own a penthouse word stays in the unit, before or after the street line")
        void shouldKeepPenthouseWordInUnitOnOwnLine(String unitLine, String unit) {
            for (String lines : List.of("123 MAIN ST\n" + unitLine, unitLine + "\n123 MAIN ST")) {
                var c = parser.parseAddress("JOHN SMITH\n" + lines + CITY_LINE).components();
                assertThat(c.unitNumber()).as(lines).isEqualTo(unit);
                assertThat(c.streetNumber()).as(lines).isEqualTo("123");
                assertThat(c.streetName()).as(lines).isEqualTo("MAIN");
                assertThat(c.careOf()).as(lines).isEmpty();
            }
        }

        @Test
        @DisplayName("A short unit joined to its unit word is split off a street line that has no street number")
        void shouldSplitShortJoinedUnitFromStreetWithoutNumber() {
            var c = parser.parseAddress("JOHN SMITH\nAPT5 MAIN ST" + CITY_LINE).components();
            assertThat(c.unitNumber()).isEqualTo("5");
            assertThat(c.streetNumber()).isEmpty();
            assertThat(c.streetName()).isEqualTo("MAIN");
            assertThat(c.streetType()).isEqualTo("ST");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "APT 204 BUZZER 1234             | 204",
                    "APT 204, BUZZ 1234              | 204",
                    "APT 204 (BUZZER 1234)           | 204",
                    "SUITE 3400 FIRST CANADIAN PLACE | 3400"
                })
        @DisplayName("Known limit: beside a clear street line, the text after the unit on a unit line is discarded")
        void shouldStillDiscardTextAfterUnitOnUnitLine(String line, String unit) {
            // A buzzer code or a building name written after the unit is not kept anywhere. The parser has no
            // field for it yet; what matters here is that the street is no longer overwritten with it.
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\n" + line + CITY_LINE)
                    .components();
            assertThat(c.unitNumber()).isEqualTo(unit);
            assertThat(c.streetNumber()).isEqualTo("123");
            assertThat(c.streetName()).isEqualTo("MAIN");
            assertThat(c.careOf()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
                delimiter = '|',
                value = {
                    "OFFICE 416-555-1234 | 416-555-1234",
                    "PH: 416-555-1234    | PH416-555-1234",
                    "APT 1234567         | 1234567",
                    "APT1234567          | 1234567",
                    "PH 416-555-1234     | PH416-555-1234",
                    "PH 4165551234       | PH4165551234",
                    "PH4165551234        | PH4165551234"
                })
        @DisplayName(
                "Known limit: a telephone number after a unit word is returned as the unit; the street is left alone")
        void shouldStillReadTelephoneNumberAfterUnitWordAsUnit(String line, String unit) {
            // The length of a value is not checked. Whatever follows the unit word on a unit line is the unit, and
            // the street line keeps its own fields, with a street type on it or not.
            for (String street : List.of("123 MAIN ST", "2200 KINGSWAY")) {
                var alone =
                        parser.parseAddress("JOHN SMITH\n" + street + CITY_LINE).components();
                var c = parser.parseAddress("JOHN SMITH\n" + street + "\n" + line + CITY_LINE)
                        .components();
                assertThat(c.unitNumber()).as(street).isEqualTo(unit);
                assertThat(c.streetNumber()).as(street).isEqualTo(street.substring(0, street.indexOf(' ')));
                assertThat(c.streetName()).as(street).isEqualTo(alone.streetName());
                assertThat(c.careOf()).as(street).isEmpty();
            }
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "APT A, TORONTO ON M5V 2Y7, CANADA",
                    "APT A, TORONTO ON M5V 2Y7,",
                    "APT A, FLOOR 3 TORONTO ON M5V 2Y7, CANADA",
                    "APT A, 123, RUE PRINCIPALE TORONTO ON M5V 2Y7"
                })
        @DisplayName(
                "Known limit: a unit first on one line with no street segment after it; province and postal code stay")
        void shouldKeepRegionWhenNoStreetFollowsLeadingUnitOnSingleLine(String address) {
            // The unit is not read and ends up in the municipality, as before. What must not happen is the whole
            // line landing in the addressee, which loses the province and the postal code.
            var c = parser.parseAddress(address).components();
            assertThat(c.addressee()).isEmpty();
            assertThat(c.municipality()).startsWith("APT A").endsWith("TORONTO");
            assertThat(c.province()).isEqualTo("ON");
            assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
        }

        @Test
        @DisplayName("Known limit: beside a street written without a street type, a mixed unit line still replaces it")
        void shouldStillReplaceStreetWrittenWithoutType() {
            // "2200 KINGSWAY" has no street type, so the parser is not sure it is the street and reads the next
            // line the way it always did
            var c = parser.parseAddress("JOHN SMITH\n2200 KINGSWAY\nAPT 3 BSMT\nVANCOUVER BC V5N 2T5")
                    .components();
            assertThat(c.streetNumber()).isEqualTo("3");
            assertThat(c.unitNumber()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"APP4", "APP 4B", "BUREAU 200"})
        @DisplayName("Known limit: a numbered company name is read as unit and street number")
        void shouldStillReadNumberedCompanyAsUnit(String unitLine) {
            // "9123-4567" looks like unit 9123 at number 4567. The street line then gives the real number, but
            // the unit from the name line is kept over the unit line, before or after the street.
            for (String lines : List.of(unitLine + "\n123 RUE PRINCIPALE", "123 RUE PRINCIPALE\n" + unitLine)) {
                var c = parser.parseAddress("9123-4567 QUÉBEC INC\n" + lines + "\nMONTRÉAL QC H3B 1A1")
                        .components();
                assertThat(c.streetNumber()).as(lines).isEqualTo("123");
                assertThat(c.unitNumber()).as(lines).isEqualTo("9123");
            }
        }

        @Test
        @DisplayName("Known limit: a unit and the region alone on one line")
        void shouldStillPutLoneUnitInMunicipalityOnSingleLine() {
            var c = parser.parseAddress("APT A, TORONTO ON M5V 2Y7").components();
            assertThat(c.municipality()).isEqualTo("APT A TORONTO");
            assertThat(c.province()).isEqualTo("ON");
            assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
        }

        @Test
        @DisplayName("A postal code on its own line does not make the municipality line a street line")
        void shouldNotCountRegionLineAsNumberedStreetLine() {
            // "100 MILE HOUSE BC" starts with a number but is the region line, so TOWER RD is read as before
            var c = parser.parseAddress("JOHN SMITH\nTOWER RD\n100 MILE HOUSE BC\nV0K 2E0")
                    .components();
            assertThat(c.streetName()).isEqualTo("RD");
            assertThat(c.careOf()).isEmpty();
            assertThat(c.municipality()).isEqualTo("100 MILE HOUSE");

            // The same when the municipality's name ends in a word that is also a street type (an invented place)
            var park = parser.parseAddress("JOHN SMITH\nTOWER RD\n12 MILE PARK BC\nV0K 2E0")
                    .components();
            assertThat(park.streetName()).isEqualTo("RD");
            assertThat(park.careOf()).isEmpty();
            assertThat(park.municipality()).isEqualTo("12 MILE PARK");
        }

        @Test
        @DisplayName("A floor line with more after it is not a numbered street line")
        void shouldNotCountFloorLineAsNumberedStreetLine() {
            var c = parser.parseAddress("JOHN SMITH\nSTE CATHERINE\n4TH FLOOR EAST" + CITY_LINE)
                    .components();
            assertThat(c.careOf()).isNotEqualTo("STE CATHERINE");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2ND FLOOR", "FLOOR 2"})
        @DisplayName("A floor line with no street line gives a unit and an incomplete address, rejected by strict mode")
        void shouldNotInventStreetFromFloorLineAlone(String floorLine) {
            String address = "JOHN SMITH\n" + floorLine + CITY_LINE;
            var c = parser.parseAddress(address).components();
            assertThat(c.unitNumber()).isEqualTo("2");
            assertThat(c.streetNumber()).isEmpty();
            assertThat(c.addressType()).isEqualTo(AddressType.INCOMPLETE);
            assertThat(parser.parseAddress(address, AddressParserService.ParsingStrategy.STRICT)
                            .successful())
                    .isFalse();
        }

        @Test
        @DisplayName("Known limit: #4 on its own line replaces the street number, because # is dropped before parsing")
        void shouldStillReadHashNumberAsStreetNumber() {
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\n#4" + CITY_LINE)
                    .components();
            assertThat(c.unitNumber()).isEmpty();
            assertThat(c.streetNumber()).isEqualTo("4");
        }

        @Test
        @DisplayName("Known limit: a floor written in words is kept as care-of, not as a unit")
        void shouldKeepFloorInWordsAsCareOf() {
            var c = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nSECOND FLOOR" + CITY_LINE)
                    .components();
            assertThat(c.unitNumber()).isEmpty();
            assertThat(c.careOf()).isEqualTo("SECOND FLOOR");
            assertThat(c.streetNumber()).isEqualTo("123");
            assertThat(c.streetName()).isEqualTo("MAIN");
        }

        @Test
        @DisplayName("Known limit: a name before the street on a single line with commas")
        void shouldNotYetReadNameFirstOnSingleLine() {
            // Outside this change: the whole line ends up in the municipality, as it did before
            var c = parser.parseAddress("JOHN SMITH, 123 MAIN ST, TORONTO ON M5V 2Y7")
                    .components();
            assertThat(c.streetNumber()).isEmpty();
            assertThat(c.municipality()).contains("JOHN SMITH");
        }

        @Test
        @DisplayName("Known limit: with no numbered street line, a unit word and a longer word is read as a street")
        void shouldStillReadUnitWordLineAsStreetWhenNoOtherStreet() {
            var c = parser.parseAddress("JOHN SMITH\nTOWER RD" + CITY_LINE).components();
            assertThat(c.unitNumber()).isEmpty();
            assertThat(c.streetName()).isEqualTo("RD");

            // A floor line starts with a number but is not a street line, so the result is the same with one
            var withFloor = parser.parseAddress("JOHN SMITH\nTOWER RD\n2ND FLOOR" + CITY_LINE)
                    .components();
            assertThat(withFloor.unitNumber()).isEqualTo("2");
            assertThat(withFloor.streetName()).isEqualTo("RD");
        }

        @Test
        @DisplayName("A street line that starts with a unit word is read even when another line starts with a number")
        void shouldReadUnitWordStreetLineBesideAnotherNumberedLine() {
            // "3M CANADA" starts with a number, so it counts as a numbered line; the real street line follows
            var c = parser.parseAddress("3M CANADA\nSUITE 200 300 TARTAN DR\nLONDON ON N5V 4M9")
                    .components();
            assertThat(c.unitNumber()).isEqualTo("200");
            assertThat(c.streetNumber()).isEqualTo("300");
            assertThat(c.streetName()).isEqualTo("TARTAN");
            assertThat(c.streetType()).isEqualTo("DR");
        }

        @ParameterizedTest
        @ValueSource(strings = {"4TH LINE", "2ND AVE", "5E RANG"})
        @DisplayName("An ordinal before a word other than a floor word is not a unit line")
        void shouldNotTakeOrdinalStreetAsFloor(String line) {
            var c = parser.parseAddress("JOHN SMITH\n" + line + "\nORO-MEDONTE ON L0L 2E0")
                    .components();
            assertThat(c.unitNumber()).isEmpty();
            assertThat(c.streetNumber()).isEqualTo(line.substring(0, line.indexOf(' ')));
        }

        @Test
        @DisplayName("A street line that starts with a unit word is still a street line")
        void shouldKeepReadingStreetLineThatStartsWithUnitWord() {
            // Another line starts with a number here ("2ND FLOOR"), but it is a floor, not the street
            var c = parser.parseAddress("JOHN SMITH\nAPT 394 QUEEN ST\n2ND FLOOR" + CITY_LINE)
                    .components();
            assertThat(c.streetNumber()).isEqualTo("394");
            assertThat(c.streetName()).isEqualTo("QUEEN");
            assertThat(c.streetType()).isEqualTo("ST");
            assertThat(c.unitNumber()).isEqualTo("2");
        }

        @Test
        @DisplayName("Known limit: a street number written after a unit word, above a street with no number")
        void shouldReadNumberAboveNumberlessStreetAsUnit() {
            // On one line ("APT 394 QUEEN ST") 394 is the street number. Split over two lines it is read as a unit.
            var c = parser.parseAddress("JOHN SMITH\nAPT 394\nQUEEN ST" + CITY_LINE)
                    .components();
            assertThat(c.unitNumber()).isEqualTo("394");
            assertThat(c.streetNumber()).isEmpty();
            assertThat(c.streetName()).isEqualTo("QUEEN");
        }
    }
}
