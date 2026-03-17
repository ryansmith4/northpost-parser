package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.guidedbyte.address.service.AddressParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Missing Element Handling")
class MissingElementsTest {

    private AddressParserService parser;

    @BeforeEach
    void setUp() {
        parser = new AddressParserService();
    }

    @Test
    @DisplayName("missing region - addressee + delivery only")
    void missingRegion() {
        var r = parser.parseAddress("JOHN SMITH\n123 MAIN ST");
        System.out.println("Missing region: " + r.components());
        assertThat(r.successful()).isTrue();
    }

    @Test
    @DisplayName("missing delivery - addressee + region only")
    void missingDelivery() {
        var r = parser.parseAddress("JOHN SMITH\nTORONTO ON M5V 2Y7");
        var c = r.components();
        System.out.println("Missing delivery: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.addressee()).isEqualTo("JOHN SMITH");
        assertThat(c.municipality()).isEqualTo("TORONTO");
        assertThat(c.province()).isEqualTo("ON");
        assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
        assertThat(c.streetNumber()).isEmpty();
        assertThat(c.streetName()).isEmpty();
    }

    @Test
    @DisplayName("missing addressee - delivery + region only")
    void missingAddressee() {
        var r = parser.parseAddress("123 MAIN ST\nTORONTO ON M5V 2Y7");
        var c = r.components();
        System.out.println("Missing addressee: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.addressee()).isEmpty();
        assertThat(c.streetNumber()).isEqualTo("123");
        assertThat(c.streetName()).isEqualTo("MAIN");
        assertThat(c.streetType()).isEqualTo("ST");
        assertThat(c.municipality()).isEqualTo("TORONTO");
        assertThat(c.province()).isEqualTo("ON");
        assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
    }

    @Test
    @DisplayName("missing postal code")
    void missingPostalCode() {
        var r = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nTORONTO ON");
        var c = r.components();
        System.out.println("Missing postal: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.addressee()).isEqualTo("JOHN SMITH");
        assertThat(c.streetNumber()).isEqualTo("123");
        assertThat(c.streetName()).isEqualTo("MAIN");
        assertThat(c.streetType()).isEqualTo("ST");
        assertThat(c.municipality()).isEqualTo("TORONTO");
        assertThat(c.province()).isEqualTo("ON");
        assertThat(c.postalCode()).isEmpty();
    }

    @Test
    @DisplayName("missing municipality")
    void missingMunicipality() {
        var r = parser.parseAddress("JOHN SMITH\n123 MAIN ST\nON M5V 2Y7");
        var c = r.components();
        System.out.println("Missing municipality: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.province()).isEqualTo("ON");
        assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
    }

    @Test
    @DisplayName("missing street type")
    void missingStreetType() {
        var r = parser.parseAddress("JOHN SMITH\n123 MAIN\nTORONTO ON M5V 2Y7");
        var c = r.components();
        System.out.println("Missing type: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.streetNumber()).isEqualTo("123");
        assertThat(c.streetName()).isEqualTo("MAIN");
        assertThat(c.streetType()).isEmpty();
        assertThat(c.municipality()).isEqualTo("TORONTO");
    }

    @Test
    @DisplayName("missing street number - street name only")
    void missingStreetNumber() {
        var r = parser.parseAddress("JOHN SMITH\nMAIN STREET\nTORONTO ON M5V 2Y7");
        var c = r.components();
        System.out.println("Missing number: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.streetName()).isEqualTo("MAIN");
        assertThat(c.streetType()).isEqualTo("STREET");
        assertThat(c.streetNumber()).isEmpty();
        assertThat(c.municipality()).isEqualTo("TORONTO");
    }

    @Test
    @DisplayName("postal code only")
    void postalCodeOnly() {
        var r = parser.parseAddress("M5V 2Y7");
        var c = r.components();
        System.out.println("Postal only: " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
        assertThat(c.province()).isEqualTo("ON"); // inferred from M
    }

    @Test
    @DisplayName("city and province only - treated as addressee (ambiguous without postal code)")
    void cityAndProvinceOnly() {
        var r = parser.parseAddress("TORONTO ON");
        var c = r.components();
        System.out.println("City+province: " + c);
        assertThat(r.successful()).isTrue();
        // Single line without postal code or comma is ambiguous — treated as addressee
        assertThat(c.addressee()).isEqualTo("TORONTO ON");
        assertThat(c.province()).isEmpty();
    }

    @Test
    @DisplayName("city and province with postal code - single line treated as addressee")
    void cityProvinceAndPostalCode() {
        // Single line without comma or street number is ambiguous — treated as addressee
        // Use multi-line or comma format for region-only input
        var r = parser.parseAddress("TORONTO ON M5V 2Y7");
        var c = r.components();
        System.out.println("City+province+postal (single line): " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.addressee()).isEqualTo("TORONTO ON M5V 2Y7");
    }

    @Test
    @DisplayName("city and province with postal code - multi-line with addressee works")
    void cityProvinceAndPostalCodeMultiLine() {
        var r = parser.parseAddress("JOHN SMITH\nTORONTO ON M5V 2Y7");
        var c = r.components();
        System.out.println("City+province+postal (multi-line): " + c);
        assertThat(r.successful()).isTrue();
        assertThat(c.addressee()).isEqualTo("JOHN SMITH");
        assertThat(c.municipality()).isEqualTo("TORONTO");
        assertThat(c.province()).isEqualTo("ON");
        assertThat(c.postalCode()).isEqualTo("M5V 2Y7");
    }

    @Test
    @DisplayName("two lines - no region - delivery becomes region line")
    void twoLinesNoRegion() {
        var r = parser.parseAddress("JOHN SMITH\n123 MAIN ST");
        var c = r.components();
        System.out.println("Two lines no region: " + c);
        assertThat(r.successful()).isTrue();
        // Last line treated as region - 123 MAIN ST won't have province/postal
        // but parser should still succeed in lenient mode
    }
}
