import csv
from dataclasses import FrozenInstanceError, replace
from pathlib import Path

import pytest

from northpost_parser import (
    AddressComponents,
    AddressNormalizer,
    AddressParserService,
    AddressType,
    NormalizationStrategy,
    ParsingMode,
    ParsingResult,
    ParsingStrategy,
)


@pytest.fixture
def parser():
    return AddressParserService()


@pytest.mark.parametrize("has_addressee", [True, False])
@pytest.mark.parametrize("has_postal_code", [True, False])
@pytest.mark.parametrize("strategy", list(ParsingStrategy))
def test_extraction_options(parser, has_addressee, has_postal_code, strategy):
    address = "JOHN DOE\n123 MAIN ST\nTORONTO ON M5V 2Y7"
    result = parser.parse_address(
        address, strategy, has_addressee=has_addressee, has_postal_code=has_postal_code
    )
    assert result.components.addressee == ("JOHN DOE" if has_addressee else "")
    assert result.components.postal_code == ("M5V 2Y7" if has_postal_code else "")
    assert result.components.street_number == "123"
    assert result.components.street_name == "MAIN"
    assert result.components.municipality == "TORONTO"
    assert result.components.province == "ON"
    assert "Address may be incomplete for mailing purposes" not in result.warnings
    assert result.successful == (
        strategy is not ParsingStrategy.STRICT or (has_addressee and has_postal_code)
    )
    if strategy is ParsingStrategy.STRICT:
        if not has_addressee:
            assert "Missing addressee" in result.errors
        if not has_postal_code:
            assert "Missing postal code" in result.errors
    if strategy is ParsingStrategy.STRICT_THEN_LENIENT and not (has_addressee and has_postal_code):
        assert result.mode_used is ParsingMode.LENIENT
        assert any(w.startswith("Strict parsing failed:") for w in result.warnings)


@pytest.mark.parametrize("has_addressee", [True, False])
@pytest.mark.parametrize("has_postal_code", [True, False])
def test_incomplete_warning_respects_expected_fields(parser, has_addressee, has_postal_code):
    result = parser.parse_address(
        "123 MAIN ST\nTORONTO ON",
        has_addressee=has_addressee, has_postal_code=has_postal_code,
    )
    assert ("Address may be incomplete for mailing purposes" in result.warnings) == (
        has_addressee or has_postal_code
    )


@pytest.mark.parametrize("address", [
    "123 MAIN ST\nTORONTO",
    "123 MAIN ST\nON",
    "SITE 4 BOX 23\nTORONTO ON",
])
def test_disabled_fields_do_not_hide_missing_delivery_or_region(parser, address):
    result = parser.parse_address(address, has_addressee=False, has_postal_code=False)
    assert "Address may be incomplete for mailing purposes" in result.warnings


def test_other_warnings_and_batch_respect_extraction_options(parser):
    result = parser.parse_address(
        "MAIN ST\nTORONTO ON\nCANADA", has_addressee=False, has_postal_code=False,
    )
    assert "Address may be incomplete for mailing purposes" not in result.warnings
    assert "Street name provided without street number" in result.warnings
    assert "Country specification is redundant for Canadian addresses" in result.warnings
    batch = parser.parse_addresses(
        ["123 MAIN ST\nTORONTO ON", "CP 23\nTORONTO ON", "RR 5\nTORONTO ON"],
        has_addressee=False, has_postal_code=False,
    )
    assert all(not r.warnings for r in batch)
    assert not parser.get_batch_statistics(batch).common_warnings


@pytest.mark.parametrize("delivery", ["MAIN ST", "B-1565 MAIN ST", "R R 2"])
def test_no_addressee_routes_first_line_as_delivery(parser, delivery):
    result = parser.parse_address(f"{delivery}\nTORONTO ON", has_addressee=False, has_postal_code=False)
    assert result.successful, result.errors
    assert result.components.addressee == ""
    assert result.components.municipality == "TORONTO"
    if delivery == "R R 2":
        assert result.components.rural_route == "2"
    else:
        assert result.components.street_name == "MAIN"


def test_no_addressee_single_delivery_line(parser):
    result = parser.parse_address("123 MAIN ST", has_addressee=False)
    assert result.components.addressee == ""
    assert result.components.street_number == "123"
    assert result.components.street_name == "MAIN"
    assert parser.parse_address("123 MAIN ST").components.addressee == "123 MAIN ST"


def test_street_name_before_comma_separated_civic_number(parser):
    result = parser.parse_address(
        "BRIDGE ROAD, 5\nTORONTO, ON", has_addressee=False, has_postal_code=False,
    )
    assert result.successful, result.errors
    assert result.components.street_number == "5"
    assert result.components.street_name == "BRIDGE ROAD"
    assert result.components.street_type == ""
    assert result.components.address_type == AddressType.CIVIC
    assert result.components.addressee == ""
    assert result.components.postal_code == ""
    assert result.components.municipality == "TORONTO"
    assert result.components.province == "ON"
    assert not any("without street number" in warning for warning in result.warnings)
    default = parser.parse_address("BRIDGE ROAD, 5\nTORONTO, ON")
    assert default.components.addressee == "BRIDGE ROAD , 5"
    assert default.components.street_number == ""


@pytest.mark.parametrize("address", [
    "M5V 2Y7",
    "M5V2Y7",
    "123 MAIN ST\nTORONTO ON M5V 2Y7",
    "123 MAIN ST\nTORONTO ON\nM5V 2Y7",
    "M5V 2Y7\n123 MAIN ST\nTORONTO ON",
    "123 MAIN ST, TORONTO ON M5V 2Y7",
    "123 MAIN ST\nTORONTO M5V 2Y7",
])
def test_postal_code_extraction_disabled(parser, address):
    result = parser.parse_address(address, has_postal_code=False)
    assert result.successful, result.errors
    assert result.components.postal_code == ""
    if "TORONTO" in address:
        assert result.components.municipality == "TORONTO"
    if "ON" not in address.replace("TORONTO", ""):
        assert result.components.province == ""


def test_batch_extraction_options(parser):
    address = "123 MAIN ST\nTORONTO ON M5V 2Y7"
    results = parser.parse_addresses(
        [address, address], ParsingStrategy.STRICT_THEN_LENIENT,
        has_addressee=False, has_postal_code=False,
    )
    assert len(results) == 2
    assert all(r.successful and r.mode_used is ParsingMode.LENIENT for r in results)
    assert all(r.components.addressee == r.components.postal_code == "" for r in results)
    assert parser.parse_address(address).components.postal_code == "M5V 2Y7"


def oda_cases():
    path = Path(__file__).resolve().parents[2] / "src/test/resources/oda_test_addresses.csv"
    with path.open(encoding="utf-8") as source:
        rows = csv.reader((line for line in source if line.strip() and not line.startswith("#")), delimiter="|")
        next(rows)
        return [pytest.param(row, id=row[0]) for row in rows]


@pytest.mark.parametrize("row", oda_cases())
def test_curated_oda_addresses(parser, row):
    address, number, name, kind, direction, unit, city, province, postal = [s.strip() for s in row]
    result = parser.parse_address(address.replace("\\n", "\n"))
    assert result.successful, result.errors
    c = result.components
    assert c.street_number == number.upper()
    if name:
        assert c.street_name == name.upper()
    assert c.street_type == kind.upper()
    assert c.street_direction == direction.upper()
    if unit:
        assert c.unit_number == unit.upper()
    assert c.municipality == city.upper()
    assert c.province == province.upper()
    if postal:
        assert c.postal_code.replace(" ", "") == postal.upper().replace(" ", "")


@pytest.mark.parametrize(("street", "name", "kind", "direction"), [
    ("MAIN ST", "MAIN", "ST", ""),
    ("PARK DRIVE", "PARK", "DRIVE", ""),
    ("COURT LANE", "COURT", "LANE", ""),
    ("ABBEY ROAD", "ABBEY", "ROAD", ""),
    ("THE PARKWAY", "THE PARKWAY", "", ""),
    ("THE BOULEVARD", "THE BOULEVARD", "", ""),
    ("THE DRIVE", "THE DRIVE", "", ""),
    ("GRANDE ALLÉE", "GRANDE ALLÉE", "", ""),
    ("GRANDE CÔTE", "GRANDE CÔTE", "", ""),
    ("GRAND RANG", "GRAND RANG", "", ""),
    ("AVENUE DU PARC", "DU PARC", "AVENUE", ""),
    ("CHEMIN DE LA CÔTE-DES-NEIGES", "DE LA CÔTE-DES-NEIGES", "CHEMIN", ""),
    ("BOULEVARD CÔTE-VERTU", "CÔTE-VERTU", "BOULEVARD", ""),
    ("RUE PRINCIPALE O", "PRINCIPALE", "RUE", "O"),
    ("N MAIN ST", "MAIN", "ST", "N"),
    ("E MAIN ST W", "E MAIN", "ST", "W"),
    ("MAIN ST NW", "MAIN", "ST", "NW"),
    ("VICTORIA E ST", "VICTORIA", "ST", "E"),
    ("EAST WEST RD", "EAST WEST", "RD", ""),
    ("NORTH SOUTH", "NORTH SOUTH", "", ""),
    ("AVENUE N", "AVENUE N", "AVENUE", ""),
    ("AVENUE P S", "AVENUE P", "AVENUE", "S"),
    ("AVENUE 3", "AVENUE 3", "AVENUE", ""),
    ("AVENUE 4A", "AVENUE 4A", "AVENUE", ""),
    ("AVE B NE", "AVE B", "AVE", "NE"),
    ("ROUTE 465", "465", "ROUTE", ""),
    ("AVENUE RD", "AVENUE", "RD", ""),
    ("PROMENADE DR", "PROMENADE", "DR", ""),
    ("RUE ACRES", "ACRES", "RUE", ""),
    ("MAIN DR.", "MAIN", "DR", ""),
    ("2E-ET-3E RANG", "2E-ET-3E", "RANG", ""),
    ("36/37 SIDERD", "36/37", "SIDERD", ""),
    ("6 & 10 SIDEROAD", "6 & 10", "SIDEROAD", ""),
    ("DRUMMOND RD - RTE 113", "DRUMMOND RD - RTE 113", "", ""),
    ("DRIVEWAY (THE)", "DRIVEWAY (THE)", "", ""),
    ("VALLEY GD", "VALLEY", "GD", ""),
])
def test_street_disambiguation(parser, street, name, kind, direction):
    result = parser.parse_address(f"TEST PERSON\n123 {street}\nTORONTO ON M5V 2Y7")
    assert result.successful, result.errors
    assert (result.components.street_name, result.components.street_type,
            result.components.street_direction) == (name, kind, direction)


@pytest.mark.parametrize(("province", "kind"), [("QC", ""), ("ON", "BOULEVARD")])
def test_french_article_province_guard(parser, province, kind):
    result = parser.parse_address(f"TEST PERSON\n123 GRAND BOULEVARD\nCITY {province}")
    assert result.components.street_type == kind


@pytest.mark.parametrize(("delivery", "unit", "number"), [
    ("APT 5 123 MAIN ST", "5", "123"),
    ("APT A 123 MAIN ST", "A", "123"),
    ("APT 5A 123 MAIN ST", "5A", "123"),
    ("APT 123 MAIN ST", "", "123"),
    ("APT5 123 MAIN ST", "5", "123"),
    ("APT.5 123 MAIN ST", "5", "123"),
    ("STE200 123 MAIN ST", "200", "123"),
    ("5-123 MAIN ST", "5", "123"),
    ("B-1565 MAIN ST", "B", "1565"),
    ("A-712 MAIN ST", "A", "712"),
    ("5B-1685 MAIN ST", "5B", "1685"),
    ("B5-1685 MAIN ST", "B5", "1685"),
    ("PH5-1685 MAIN ST", "PH5", "1685"),
    ("PH-1685 MAIN ST", "PH", "1685"),
    ("300B-1685 MAIN ST", "300B", "1685"),
    ("APP 1/2 123 MAIN ST", "1/2", "123"),
    ("APT 405-2 123 MAIN ST", "405-2", "123"),
    ("APT 1101.1 123 MAIN ST", "1101.1", "123"),
    ("123 MAIN ST APT 5", "5", "123"),
    ("123 MAIN ST NW APT 5", "5", "123"),
    ("123 MAIN ST APT5", "5", "123"),
    ("123 1/2 MAIN ST", "", "123 1/2"),
    ("123 3/4 MAIN ST", "", "123 3/4"),
    ("123/125 MAIN ST", "", "123/125"),
    ("(123) MAIN ST", "", "123"),
    ("123A MAIN ST", "", "123A"),
])
def test_civic_and_units(parser, delivery, unit, number):
    result = parser.parse_address(f"TEST PERSON\n{delivery}\nTORONTO ON M5V 2Y7")
    assert result.successful, result.errors
    assert result.components.unit_number == unit
    assert result.components.street_number == number
    assert result.components.street_name == "MAIN"
    assert result.components.street_type == "ST"


@pytest.mark.parametrize("unit", list("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
@pytest.mark.parametrize("addressee", ["", "TEST PERSON\n"])
def test_single_letter_unit_hyphen_civic(parser, unit, addressee):
    number = "1565" if unit == "B" else "712"
    result = parser.parse_address(f"{addressee}{unit.lower()}-{number} MAIN ST\nTORONTO ON M5V 2Y7")
    assert result.successful, result.errors
    assert result.components.unit_number == unit
    assert result.components.street_number == number
    assert result.components.street_name == "MAIN"
    assert result.components.street_type == "ST"
    assert result.components.street_direction == ""
    assert result.components.address_type == AddressType.CIVIC
    assert result.components.addressee == addressee.strip()


@pytest.mark.parametrize(("delivery", "kind", "field", "value"), [
    ("PO BOX 123", AddressType.POSTAL_BOX, "postal_box_number", "123"),
    ("BOX 123", AddressType.POSTAL_BOX, "postal_box_number", "123"),
    ("P.O. BOX 123", AddressType.POSTAL_BOX, "postal_box_number", "123"),
    ("CP 123", AddressType.POSTAL_BOX, "postal_box_number", "123"),
    ("C.P. 123", AddressType.POSTAL_BOX, "postal_box_number", "123"),
    ("RR 2", AddressType.RURAL_ROUTE, "rural_route", "2"),
    ("R.R. 2", AddressType.RURAL_ROUTE, "rural_route", "2"),
    ("GD", AddressType.GENERAL_DELIVERY, "street_name", ""),
    ("GENERAL DELIVERY", AddressType.GENERAL_DELIVERY, "street_name", ""),
    ("GEN DEL", AddressType.GENERAL_DELIVERY, "street_name", ""),
    ("GEN DEL.", AddressType.GENERAL_DELIVERY, "street_name", ""),
])
def test_delivery_types(parser, delivery, kind, field, value):
    result = parser.parse_address(f"TEST PERSON\n{delivery}\nCITY ON M5V 2Y7")
    assert result.successful, result.errors
    assert result.components.address_type == kind
    assert getattr(result.components, field) == value


@pytest.mark.parametrize("prefix", ["CP", "C.P", "C.P.", "CASE POSTALE", "case postale"])
@pytest.mark.parametrize("layout", ["single", "comma", "multiline", "addressee"])
def test_french_postal_box_variants(parser, prefix, layout):
    delivery = f"{prefix} 2086"
    address = {
        "single": delivery,
        "comma": f"{delivery}, QUEBEC, QC",
        "multiline": f"{delivery}\nQUEBEC QC",
        "addressee": f"ENTREPRISE\n{delivery}\nQUEBEC QC",
    }[layout]
    result = (
        parser.parse_address(address, has_addressee=False, has_postal_code=False)
        if layout == "single" else parser.parse_address(address)
    )
    assert result.successful, result.errors
    assert result.components.postal_box_number == "2086"
    assert result.components.address_type == AddressType.POSTAL_BOX
    assert result.components.street_number == ""
    assert result.components.care_of == ""
    assert result.components.addressee == ("ENTREPRISE" if layout == "addressee" else "")
    if layout != "single":
        assert result.components.municipality == "QUEBEC"
        assert result.components.province == "QC"


def test_case_without_postale_is_not_postal_box(parser):
    result = parser.parse_address("CASE COMPANY\n123 MAIN ST\nTORONTO ON")
    assert result.components.addressee == "CASE COMPANY"
    assert result.components.postal_box_number == ""
    assert result.components.address_type == AddressType.CIVIC


@pytest.mark.parametrize("prefix", ["RR", "R.R.", "R R", "R. R.", "r r", "R\tR"])
@pytest.mark.parametrize("addressee", ["", "FARM OWNER\n"])
def test_rural_route_prefix_variants(parser, prefix, addressee):
    result = parser.parse_address(f"{addressee}{prefix} 2 STN MAIN\nTORONTO ON M5V 2Y7")
    assert result.successful, result.errors
    assert result.components.address_type == AddressType.RURAL_ROUTE
    assert result.components.rural_route == "2"
    assert result.components.street_number == ""
    assert result.components.addressee == addressee.strip()
    assert result.components.municipality == "TORONTO"


@pytest.mark.parametrize("prefix", ["RR", "R.R.", "R R", "R. R."])
@pytest.mark.parametrize("site", ["SITE 4 BOX 23", "SITE 4 COMP 23", "COMP 23", "EMPL 4"])
@pytest.mark.parametrize("layout", ["multiline", "single", "comma"])
def test_rural_route_on_site_line(parser, prefix, site, layout):
    delivery = f"{site} {prefix} 5"
    address = {
        "multiline": f"{delivery}\nTORONTO ON",
        "single": delivery,
        "comma": f"{delivery}, TORONTO ON",
    }[layout]
    result = parser.parse_address(address, has_addressee=False, has_postal_code=False)
    assert result.successful, result.errors
    assert result.components.site_info == site
    assert result.components.rural_route == "5"
    assert result.components.address_type == AddressType.RURAL_ROUTE
    assert result.components.postal_box_number == ""
    assert result.components.street_number == ""
    assert result.components.addressee == result.components.postal_code == ""
    if layout != "single":
        assert result.components.municipality == "TORONTO"
        assert result.components.province == "ON"


@pytest.mark.parametrize("site", ["SITE 4 BOX 23", "SITE 4 BOX 23 RR", "SITE 4 BOX 23 R R"])
def test_site_without_valid_rural_route_suffix(parser, site):
    result = parser.parse_address(f"{site}\nTORONTO ON", has_addressee=False)
    assert result.components.site_info == site
    assert result.components.rural_route == ""
    assert result.components.postal_box_number == ""


def test_rural_route_with_care_of(parser):
    result = parser.parse_address("C/O JOHN DORR\nRR 4\nOTTAWA, ON")
    assert result.successful, result.errors
    assert result.errors == ()
    assert result.components.care_of == "JOHN DORR"
    assert result.components.rural_route == "4"
    assert result.components.address_type == AddressType.RURAL_ROUTE
    assert result.components.municipality == "OTTAWA"
    assert result.components.province == "ON"
    assert result.components.addressee == ""
    assert result.components.postal_code == ""


def test_rural_route_on_site_line_with_addressee(parser):
    result = parser.parse_address("FARM OWNER\nSITE 4 BOX 23 RR 5\nTORONTO ON M5V 2Y7")
    assert result.successful, result.errors
    assert result.components.addressee == "FARM OWNER"
    assert result.components.site_info == "SITE 4 BOX 23"
    assert result.components.rural_route == "5"
    assert result.components.address_type == AddressType.RURAL_ROUTE
    assert result.components.postal_code == "M5V 2Y7"


@pytest.mark.parametrize(("region", "city", "province", "postal"), [
    ("TORONTO ON M5V 2Y7", "TORONTO", "ON", "M5V 2Y7"),
    ("TORONTO ONTARIO M5V2Y7", "TORONTO", "ON", "M5V 2Y7"),
    ("TORONTO ONT. M5V 2Y7", "TORONTO", "ON", "M5V 2Y7"),
    ("VANCOUVER B.C. V6B 1A1", "VANCOUVER", "BC", "V6B 1A1"),
    ("VANCOUVER BRITISH COLUMBIA V6B1A1", "VANCOUVER", "BC", "V6B 1A1"),
    ("ST. JOHN'S NEWFOUNDLAND AND LABRADOR A1A 1A1", "ST. JOHN'S", "NL", "A1A 1A1"),
    ("CHARLOTTETOWN PRINCE EDWARD ISLAND C1A1A1", "CHARLOTTETOWN", "PE", "C1A 1A1"),
    ("QUÉBEC QUÉBEC G1A 1A1", "QUÉBEC", "QC", "G1A 1A1"),
    ("TORONTO M5V 2Y7", "TORONTO", "ON", "M5V 2Y7"),
    ("CITY NT", "CITY", "NT", ""),
    ("CITY NU", "CITY", "NU", ""),
    ("TORONTO ON\nM5V 2Y7", "TORONTO", "ON", "M5V 2Y7"),
    ("TORONTO ON M5V 2Y7\nCANADA", "TORONTO", "ON", "M5V 2Y7"),
])
def test_regions(parser, region, city, province, postal):
    result = parser.parse_address(f"TEST PERSON\n123 MAIN ST\n{region}")
    assert result.successful, result.errors
    c = result.components
    assert (c.municipality, c.province, c.postal_code) == (city, province, postal)


@pytest.mark.parametrize("marker", ["C/O", "A/S"])
def test_anchor_classification(parser, marker):
    result = parser.parse_address(
        f"123 MAIN ST\nTEST PERSON\n{marker} OTHER PERSON\nSITE 4 COMP 2\nTORONTO ON M5V 2Y7"
    )
    assert result.successful, result.errors
    c = result.components
    assert c.addressee == "TEST PERSON"
    assert c.care_of == "OTHER PERSON"
    assert c.site_info == "SITE 4 COMP 2"
    assert c.street_number == "123"


@pytest.mark.parametrize("text", [
    "123 MAIN ST, TORONTO ON M5V 2Y7",
    "123 MAIN ST, TORONTO, ON, M5V 2Y7",
    "123 MAIN ST,\nTORONTO ON M5V 2Y7",
])
def test_no_addressee(parser, text):
    result = parser.parse_address(text)
    assert result.successful, result.errors
    assert result.components.addressee == ""
    assert result.components.street_name == "MAIN"
    assert result.components.municipality == "TORONTO"
    assert not result.components.is_complete


@pytest.mark.parametrize(("text", "expected"), [
    ("TORONTO ON", {"addressee": "TORONTO ON"}),
    ("TORONTO ON M5V 2Y7", {"addressee": "TORONTO ON M5V 2Y7"}),
    ("M5V 2Y7", {"postal_code": "M5V 2Y7", "province": "ON"}),
    ("M5V2Y7", {"postal_code": "M5V 2Y7", "province": "ON"}),
    ("ST. JOHN'S, NL A1A 1A1", {"municipality": "ST. JOHN'S", "province": "NL"}),
    ("TEST PERSON\nTORONTO ON M5V 2Y7", {"addressee": "TEST PERSON", "street_name": ""}),
    ("TEST PERSON\nMAIN STREET\nTORONTO ON", {"street_name": "MAIN", "street_number": ""}),
    ("TEST PERSON\n123 MAIN\nON", {"street_name": "MAIN", "street_type": "", "municipality": ""}),
])
def test_partial_addresses(parser, text, expected):
    result = parser.parse_address(text)
    assert result.successful, result.errors
    for field, value in expected.items():
        assert getattr(result.components, field) == value


@pytest.mark.parametrize("strategy", list(ParsingStrategy))
@pytest.mark.parametrize("text", [None, "", " \t\n"])
def test_empty_input(parser, strategy, text):
    result = parser.parse_address(text, strategy)
    assert not result.successful
    assert result.errors == ("Address text cannot be null or empty",)
    assert result.mode_used == ParsingMode.LENIENT


@pytest.mark.parametrize("strategy", list(ParsingStrategy))
def test_complete_strategies(parser, strategy):
    result = parser.parse_address("JOHN DOE\n123 MAIN ST\nTORONTO ON M5V 2Y7", strategy)
    assert result.successful and result.components.is_complete
    mode = ParsingMode.LENIENT if strategy == ParsingStrategy.LENIENT else ParsingMode.STRICT
    assert result.mode_used == result.components.parsing_mode == mode
    assert not result.errors and not result.warnings


def test_strict_fallback(parser):
    strict = parser.parse_address("JOHN DOE", ParsingStrategy.STRICT)
    assert not strict.successful
    assert strict.errors == (
        "Address is not complete for mailing purposes", "Missing delivery address",
        "Missing municipality", "Missing province", "Missing postal code",
    )
    fallback = parser.parse_address("JOHN DOE", ParsingStrategy.STRICT_THEN_LENIENT)
    assert fallback.successful and fallback.mode_used == ParsingMode.LENIENT
    assert fallback.warnings[-1] == "Strict parsing failed: " + "; ".join(strict.errors)


def test_input_normalization(parser):
    standard = parser.parse_address("JOHN DOE\n123 MAIN ST\nTORONTO ON M5V 2Y7")
    messy = parser.parse_address("\n  john   Doe \r\n\t123   main\tst \r\n Toronto  on  m5v 2y7 \n")
    assert messy == standard


def test_warnings_and_syntax_errors(parser):
    result = parser.parse_address("JOHN DOE\nMAIN ST\nTORONTO ON\nCANADA")
    assert "Country specification is redundant for Canadian addresses" in result.warnings
    assert "Address may be incomplete for mailing purposes" in result.warnings
    assert "Street name provided without street number" in result.warnings
    invalid = parser.parse_address("!!!")
    assert not invalid.successful and invalid.has_errors()
    assert invalid.errors[0].startswith("Line ")


def test_unexpected_error_is_reported(parser, monkeypatch, caplog):
    def broken_visit(*args):
        raise RuntimeError("test failure")

    monkeypatch.setattr("northpost_parser.service.AddressComponentVisitor.visit", broken_visit)
    result = parser.parse_address("JOHN DOE")
    assert not result.successful
    assert result.errors == ("Unexpected error: test failure",)
    assert "Unexpected error during parsing" in caplog.text


def test_batch_statistics(parser):
    results = parser.parse_addresses(
        ["JOHN DOE\n123 MAIN ST\nTORONTO ON M5V 2Y7", "JOHN DOE", None],
        ParsingStrategy.STRICT_THEN_LENIENT,
    )
    stats = parser.get_batch_statistics(iter(results))
    assert (stats.total_addresses, stats.successful_parses, stats.strict_mode_used,
            stats.lenient_mode_used, stats.complete_addresses) == (3, 2, 1, 2, 1)
    assert stats.common_warnings == tuple(sorted(set(w for r in results for w in r.warnings)))
    assert parser.get_batch_statistics([]).total_addresses == 0
    assert parser.parse_addresses([]) == []


def test_common_warning_threshold(parser):
    empty = ParsingResult(AddressComponents(), True, (), (), ParsingMode.LENIENT)
    rare = replace(empty, warnings=("Rare",))
    common = replace(empty, warnings=("Common",))
    stats = parser.get_batch_statistics([rare, common, common] + [empty] * 7)
    assert stats.common_warnings == ("Common",)


def test_components_and_formatting():
    c = AddressComponents(
        addressee=" John Doe ", care_of="Jane Doe", unit_number="5",
        street_number="123", street_name="Main", street_type="St", street_direction="W",
        municipality="Toronto", province="On", postal_code="M5V 2Y7", country="Canada",
        address_type=AddressType.CIVIC,
    )
    assert c.get_full_street_name() == "MAIN ST W"
    assert c.get_formatted_street_address() == "5-123 MAIN ST W"
    assert c.get_formatted_last_line() == "TORONTO ON  M5V 2Y7"
    assert c.get_formatted_address() == (
        "JOHN DOE\nC/O JANE DOE\n5-123 MAIN ST W\nTORONTO ON  M5V 2Y7\nCANADA"
    )
    assert c.is_mailing_valid()
    with pytest.raises(FrozenInstanceError):
        c.street_name = "OTHER"
    assert AddressComponents(street_name=None).street_name == ""
    assert AddressComponents().get_formatted_address() == ""
    assert AddressComponents().get_full_street_name() is None
    with pytest.raises(TypeError):
        AddressComponents(address_type=None)
    with pytest.raises(TypeError):
        AddressComponents(parsing_mode=None)


@pytest.mark.parametrize(("kind", "fields", "expected"), [
    (AddressType.POSTAL_BOX, {"postal_box_number": "123"}, "PO BOX 123"),
    (AddressType.RURAL_ROUTE, {"rural_route": "2"}, "RR 2"),
    (AddressType.GENERAL_DELIVERY, {}, "GENERAL DELIVERY"),
    (AddressType.MIXED, {}, None),
    (AddressType.INCOMPLETE, {}, None),
    (AddressType.CIVIC, {}, None),
])
def test_civic_formatting(kind, fields, expected):
    assert AddressComponents(address_type=kind, **fields).get_formatted_civic_line() == expected


@pytest.mark.parametrize(("value", "full", "abbreviated"), [
    ("ST", "STREET", "ST"), ("STREET", "STREET", "ST"),
    ("BV", "BOULEVARD", "BLVD"), ("BOUL", "BOULEVARD", "BOUL"),
    ("BOULEVARD", "BOULEVARD", "BLVD"), ("CH", "CHEMIN", "CH"),
    ("COTE", "CÔTE", "COTE"), ("CT", "COURT", "CRT"),
    ("UNKNOWN", "UNKNOWN", "UNKNOWN"), ("", "", ""),
])
def test_street_normalization(value, full, abbreviated):
    assert AddressNormalizer.normalize_street_type(value, NormalizationStrategy.FULL_FORM) == full
    assert AddressNormalizer.normalize_street_type(value, NormalizationStrategy.ABBREVIATED) == abbreviated


@pytest.mark.parametrize(("value", "full", "abbreviated"), [
    ("N", "NORTH", "N"), ("N.W.", "NORTHWEST", "NW"), ("O", "OUEST", "O"),
    ("NORD", "NORD", "N"), ("SUD", "SUD", "S"), ("EST", "EST", "E"),
    ("UNKNOWN", "UNKNOWN", "UNKNOWN"), ("", "", ""),
])
def test_direction_normalization(value, full, abbreviated):
    assert AddressNormalizer.normalize_direction(value, NormalizationStrategy.FULL_FORM) == full
    assert AddressNormalizer.normalize_direction(value, NormalizationStrategy.ABBREVIATED) == abbreviated


def test_normalization_preserves_other_components(parser):
    c = parser.parse_address("JOHN DOE\n123 MAIN ST N\nTORONTO ON M5V 2Y7").components
    normalized = c.normalize(NormalizationStrategy.FULL_FORM)
    assert normalized == replace(c, street_type="STREET", street_direction="NORTH")
    assert c.street_type == "ST"


def test_fraction_with_leading_zeros(parser):
    numerator = "0" * 5000 + "1"
    denominator = "0" * 5000 + "2"
    result = parser.parse_address(f"JOHN DOE\n123 {numerator}/{denominator} MAIN ST\nTORONTO ON")
    assert result.successful, result.errors
    assert result.components.street_number == f"123 {numerator}/{denominator}"
    assert result.components.street_name == "MAIN"
