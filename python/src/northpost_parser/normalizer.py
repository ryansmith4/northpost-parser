"""Canada Post street-type and direction normalization tables."""

from .models import NormalizationStrategy


def _register(full: dict[str, str], abbreviated: dict[str, str],
              name: str, abbreviation: str) -> None:
    full[abbreviation] = name
    full[name] = name
    abbreviated.setdefault(name, abbreviation)
    abbreviated[abbreviation] = abbreviation


def _alias(full: dict[str, str], abbreviated: dict[str, str],
           abbreviation: str, name: str) -> None:
    full[abbreviation] = name
    abbreviated[abbreviation] = abbreviated.get(name, abbreviation)


_STREET_FULL: dict[str, str] = {}
_STREET_ABBREVIATED: dict[str, str] = {}
for _name, _abbreviation in (
    ("STREET", "ST"), ("AVENUE", "AVE"), ("BOULEVARD", "BLVD"),
    ("DRIVE", "DR"), ("ROAD", "RD"), ("PLACE", "PL"), ("COURT", "CRT"),
    ("CRESCENT", "CRES"), ("CIRCLE", "CIR"), ("WAY", "WY"), ("LANE", "LN"),
    ("TERRACE", "TERR"), ("TRAIL", "TRL"), ("PARKWAY", "PKY"), ("HIGHWAY", "HWY"),
    ("CLOSE", "CL"), ("GATE", "GT"), ("GREEN", "GR"), ("GROVE", "GV"),
    ("HEATH", "HE"), ("HEIGHTS", "HTS"), ("HILL", "HL"), ("LANDING", "LD"),
    ("MEWS", "MS"), ("PARK", "PK"), ("POINT", "PT"), ("SQUARE", "SQ"),
    ("WALK", "WK"), ("BAY", "BA"), ("BRANCH", "BR"), ("CIRCUIT", "CIRC"),
    ("COMMON", "CM"), ("CONCESSION", "CON"), ("COVE", "CO"), ("CREEK", "CRK"),
    ("CROSSING", "XG"), ("ESPLANADE", "ESPL"), ("ESTATES", "ESTS"),
    ("EXTENSION", "EXTEN"), ("FREEWAY", "FWY"), ("GARDENS", "GDNS"),
    ("INLET", "INL"), ("LINE", "LI"), ("LOOKOUT", "LKOUT"), ("MANOR", "MR"),
    ("MOUNT", "MT"), ("MOUNTAIN", "MTN"), ("PASSAGE", "PASS"), ("PRIVATE", "PVT"),
    ("PROMENADE", "PROM"), ("RANGE", "RG"), ("RISE", "RI"), ("SIDEROAD", "SDRD"),
    ("TOWNLINE", "TL"), ("TRUNK", "TR"), ("VIEW", "VW"),
):
    _register(_STREET_FULL, _STREET_ABBREVIATED, _name, _abbreviation)

for _abbreviation, _name in (
    ("AV", "AVENUE"), ("BV", "BOULEVARD"), ("DV", "DRIVE"), ("CT", "COURT"),
    ("CR", "CRESCENT"), ("CI", "CIRCLE"), ("PY", "PARKWAY"), ("HY", "HIGHWAY"),
    ("CLO", "CLOSE"), ("GA", "GATE"), ("GRN", "GREEN"), ("GRV", "GROVE"),
    ("HT", "HEIGHTS"), ("LANDNG", "LANDING"), ("PA", "PARK"), ("CV", "COVE"),
    ("CDS", "CROSSING"), ("GDN", "GARDENS"), ("PR", "PRIVATE"), ("RO", "RISE"),
    ("SR", "SIDEROAD"), ("VI", "VIEW"), ("ME", "MAZE"), ("TC", "THICKET"),
    ("TE", "THICKET"), ("XG", "CROSSING"),
):
    _alias(_STREET_FULL, _STREET_ABBREVIATED, _abbreviation, _name)

for _name, _abbreviation in (
    ("BOULEVARD", "BOUL"), ("CHEMIN", "CH"), ("ROUTE", "RTE"),
    ("IMPASSE", "IMP"), ("TERRASSE", "TSSE"), ("CARRÉ", "CAR"),
):
    _register(_STREET_FULL, _STREET_ABBREVIATED, _name, _abbreviation)
for _abbreviation, _name in (
    ("BL", "BOULEVARD"), ("CMN", "CHEMIN"), ("CARRE", "CARRÉ"),
    ("MONTEE", "MONTÉE"), ("COTE", "CÔTE"), ("ALLEE", "ALLÉE"), ("CRETE", "CRÊTE"),
):
    _alias(_STREET_FULL, _STREET_ABBREVIATED, _abbreviation, _name)

_DIRECTION_FULL: dict[str, str] = {}
_DIRECTION_ABBREVIATED: dict[str, str] = {}
for _name, _abbreviation in (
    ("NORTH", "N"), ("SOUTH", "S"), ("EAST", "E"), ("WEST", "W"),
    ("NORTHEAST", "NE"), ("NORTHWEST", "NW"), ("SOUTHEAST", "SE"), ("SOUTHWEST", "SW"),
    ("NORTHBOUND", "NB"), ("SOUTHBOUND", "SB"), ("EASTBOUND", "EB"), ("WESTBOUND", "WB"),
):
    _register(_DIRECTION_FULL, _DIRECTION_ABBREVIATED, _name, _abbreviation)
for _abbreviation, _name in (
    ("N.", "NORTH"), ("S.", "SOUTH"), ("E.", "EAST"), ("W.", "WEST"),
    ("N.E.", "NORTHEAST"), ("N.W.", "NORTHWEST"), ("S.E.", "SOUTHEAST"), ("S.W.", "SOUTHWEST"),
):
    _alias(_DIRECTION_FULL, _DIRECTION_ABBREVIATED, _abbreviation, _name)
for _name, _abbreviation in (("NORD", "N"), ("SUD", "S"), ("EST", "E"), ("OUEST", "O")):
    _DIRECTION_FULL[_name] = _name
    _DIRECTION_ABBREVIATED[_name] = _abbreviation
_DIRECTION_FULL["O"] = "OUEST"
_DIRECTION_ABBREVIATED["O"] = "O"


def _normalize(value: str | None, strategy: NormalizationStrategy,
               full: dict[str, str], abbreviated: dict[str, str]) -> str | None:
    if not value:
        return value
    match strategy:
        case NormalizationStrategy.FULL_FORM:
            return full.get(value.upper(), value)
        case NormalizationStrategy.ABBREVIATED:
            return abbreviated.get(value.upper(), value)
        case _:
            raise ValueError(f"Unknown normalization strategy: {strategy!r}")


class AddressNormalizer:
    """Normalize known values; preserve unknown and empty values unchanged."""

    @staticmethod
    def normalize_street_type(street_type: str | None,
                              strategy: NormalizationStrategy) -> str | None:
        return _normalize(street_type, strategy, _STREET_FULL, _STREET_ABBREVIATED)

    @staticmethod
    def normalize_direction(direction: str | None,
                            strategy: NormalizationStrategy) -> str | None:
        return _normalize(direction, strategy, _DIRECTION_FULL, _DIRECTION_ABBREVIATED)
