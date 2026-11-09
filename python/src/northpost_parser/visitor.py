"""Semantic interpretation of the shared, deliberately minimal address grammar."""

from dataclasses import dataclass, replace
import re

from .generated.CanadianAddressParser import CanadianAddressParser as P
from .generated.CanadianAddressVisitor import CanadianAddressVisitor
from .models import AddressComponents, AddressType, ParsingMode


PROVINCE_CODES = set("AB BC MB NB NL NT NS NU ON PE QC SK YT".split())
POSTAL_PREFIX_TO_PROVINCE = dict(zip("ABCEGHJKLMNPRSTVXY",
                                    "NL NS PE NB QC QC QC ON ON ON ON ON MB SK AB BC NT YT".split()))
STREET_TYPES_EN = set("""
STREET ST AVENUE AVE AV BOULEVARD BLVD BV DRIVE DR DV ROAD RD PLACE PL COURT CRT CT
CRESCENT CRES CR CIRCLE CIR CI WAY WY LANE LN TERRACE TERR TRAIL TRL PARKWAY PKY PY
HIGHWAY HWY HY CLOSE CL CLO GATE GT GA GREEN GR GRN GROVE GV GRV HEATH HE HEIGHTS
HTS HT HILL HL LANDING LANDNG LD MEWS MS PARADE PARK PK PA PATH POINT PT RIDGE ROW
SQUARE SQ WALK WK ABBEY ACRES ALLEY BAY BA BEACH BEND BRANCH BR BYPASS BYWAY CAPE
CENTRE CHASE CIRCUIT CIRC COMMON CM CONCESSION CON CORNERS COVE CO CV CREEK CRK
CROSS CROSSING XG CDS DALE DELL DIVERSION DOWNS END ESPLANADE ESPL ESTATES ESTS
EXTENSION EXTEN FAIRWAY FARM FIELD FORD FOREST FREEWAY FWY FRONT GARDEN GARDENS
GDNS GDN GLADE GLEN GRANGE HARBOUR HAVEN HOLLOW INLET INL ISLAND KEY KNOLL LIMITS
LINE LI LINK LOOKOUT LKOUT LOOP MALL MANOR MR MAZE ME MEADOW MEADOWS MOUNT MT
MOUNTAIN MTN ORCHARD PARKLAND PASSAGE PASS PINES PLATEAU PLAZA PORT PRIVATE PVT PR
PROMENADE PROM QUAY RAMP RANGE RG REACH RISE RI RO RUN SHORE SHORES SHORELINE
SIDEROAD SDRD SR SPRING STROLL TC TE THICKET TOWERS TOWNLINE TL TRUNK TR TUNNEL
TURNABOUT VALE VIEW VW VI VILLAGE VILLAS VISTA WHARF WOOD WOODS WYND PKWY CREST
BLUFF SPUR ESTATE GATEWAY GY WALKWAY OUTLOOK GD CA CX PS PZ LP AL HI GL PW CE LK GW
SIDERD CONC TLINE CIRCT VILLGE PATHWAY PTWAY DRIVEWAY DRWY CTR POINTE HIGHLANDS HGHLDS HARBR
""".split())
STREET_TYPES_FR = set("""
RUE AVENUE AVE AV BOULEVARD BOUL BL CHEMIN CH CMN ROUTE RTE PLACE RANG MONTÉE MONTEE
CÔTE COTE CROISSANT IMPASSE IMP PASSAGE PROMENADE TERRASSE TSSE ALLÉE ALLEE CARRÉ
CARRE CAR CERCLE CIRCUIT COUR COURS CRÊTE CRETE PARC PONT SENTIER TUNNEL VOIE RUELLE
AUTOROUTE DESSERTE JARDIN QUAI DIVERS CROIS RLE SENT
""".split())
ALL_STREET_TYPES = STREET_TYPES_EN | STREET_TYPES_FR
TYPE_WORDS_USED_AS_NAMES = set("""
ACRES BAY BEACH BEND CAPE CENTRE CHASE COMMON COURT COVE CREEK CRESCENT CROSSING DALE
DELL DOWNS END ESTATE ESTATES FARM FIELD FORD FOREST FRONT GARDEN GARDENS GLEN
GRANGE GREEN GROVE HARBOUR HAVEN HEATH HEIGHTS HILL HOLLOW ISLAND KEY KNOLL LANDING
LINK LOOKOUT MALL MANOR MEADOW MEADOWS MOUNT MOUNTAIN ORCHARD PARK PINES PLATEAU
POINT PORT RANCH REACH RIDGE RISE RUN SHORE SHORES SPRING SQUARE TERRACE TOWERS
VALE VIEW VILLAGE VISTA WALK WHARF WOOD WOODS RUE CHEMIN ROUTE RANG PLACE BOULEVARD
AVENUE ALLÉE ALLEE CÔTE COTE PARC PASSAGE PROMENADE CROISSANT IMPASSE SENTIER CIRCUIT
CERCLE TUNNEL MONTÉE MONTEE VOIE RUELLE TERRASSE
""".split())
PREFIX_DIRECTIONS = set("N S E W O NE NW SE SW N. S. E. W.".split())
DIRECTIONS = PREFIX_DIRECTIONS | set("""
NB SB EB WB NORTH SOUTH EAST WEST NORTHEAST NORTHWEST SOUTHEAST SOUTHWEST
NORTHBOUND SOUTHBOUND EASTBOUND WESTBOUND N.E. N.W. S.E. S.W. EST OUEST NORD SUD
""".split())
UNIT_DESIGNATORS = set("""
APT APARTMENT SUITE STE UNIT BUREAU BUR ROOM RM FLOOR FL OFFICE DEPT DEPARTMENT
BLDG BUILDING TOWER TWR PH PENTHOUSE APP APPARTEMENT PORTE ÉTAGE ETAGE
""".split())
UNIT_DESIGNATORS_BY_LENGTH = sorted(UNIT_DESIGNATORS, key=lambda x: (-len(x), x))
PROVINCE_NAMES = {
    "NEWFOUNDLAND AND LABRADOR": "NL", "PRINCE EDWARD ISLAND": "PE",
    "BRITISH COLUMBIA": "BC", "NEW BRUNSWICK": "NB", "NORTHWEST TERRITORIES": "NT",
    "NOVA SCOTIA": "NS", "SASKATCHEWAN": "SK", "NEWFOUNDLAND": "NL",
    "MANITOBA": "MB", "ONTARIO": "ON", "ALBERTA": "AB", "NUNAVUT": "NU",
    "QUEBEC": "QC", "QUÉBEC": "QC", "YUKON": "YT", "NFLD.": "NL", "NFLD": "NL",
    "P.E.I.": "PE", "PEI": "PE", "N.S.": "NS", "N.B.": "NB", "QUE.": "QC",
    "QUE": "QC", "ONT.": "ON", "ONT": "ON", "MAN.": "MB", "MAN": "MB",
    "SASK.": "SK", "SASK": "SK", "ALTA.": "AB", "ALTA": "AB", "B.C.": "BC",
    "N.W.T.": "NT", "NWT": "NT", "Y.T.": "YT",
}
COUNTRY_KEYWORDS = {"CANADA", "CAN", "CA"}
PO_BOX_KEYWORDS = {"PO", "P.O.", "CP", "C.P", "C.P.", "BOX"}
NUMERIC_TYPES = {P.NUMBER, P.ALPHANUMERIC}
WORD_TYPES = NUMERIC_TYPES | {P.WORD}


def normalize_street_type(word: str) -> str | None:
    if word in ALL_STREET_TYPES:
        return word
    stripped = word.removesuffix(".")
    return stripped if stripped in ALL_STREET_TYPES else None


def is_french_street_type(word: str) -> bool:
    return word.removesuffix(".") in STREET_TYPES_FR


def is_unit_designator(word: str) -> bool:
    return word.removesuffix(".") in UNIT_DESIGNATORS


def unit_prefix(word: str) -> tuple[str, str] | None:
    for designator in UNIT_DESIGNATORS_BY_LENGTH:
        if word.startswith(designator):
            remainder = word[len(designator):].removeprefix(".")
            if remainder and remainder[0].isdigit():
                return designator, remainder
    return None


def opposing_directions(first: str, second: str) -> bool:
    opposites = {
        "EAST": {"WEST", "OUEST"}, "EST": {"WEST", "OUEST"},
        "WEST": {"EAST", "EST"}, "OUEST": {"EAST", "EST"},
        "NORTH": {"SOUTH", "SUD"}, "NORD": {"SOUTH", "SUD"},
        "SOUTH": {"NORTH", "NORD"}, "SUD": {"NORTH", "NORD"},
    }
    return second in opposites.get(first, ())


def is_fsa(text: str) -> bool:
    return re.fullmatch(r"[A-Z]\d[A-Z]", text) is not None


def is_ldu(text: str) -> bool:
    return re.fullmatch(r"\d[A-Z]\d", text) is not None


def format_postal_code(raw: str) -> str:
    compact = re.sub(r"\s", "", raw.upper())
    return compact[:3] + " " + compact[3:] if len(compact) == 6 else compact


@dataclass(frozen=True)
class TokenInfo:
    type: int
    text: str
    start: int
    stop: int


@dataclass(frozen=True)
class LineTokens:
    tokens: list[TokenInfo]
    text: str


def line_from_tokens(tokens: list[TokenInfo]) -> LineTokens:
    return LineTokens(tokens, " ".join(t.text for t in tokens))


def adjacent(first: TokenInfo, second: TokenInfo) -> bool:
    return first.stop + 1 == second.start


def care_of(tokens: list[TokenInfo]) -> bool:
    return (len(tokens) >= 3 and tokens[1].type == P.SLASH
            and (tokens[0].text, tokens[2].text) in {("C", "O"), ("A", "S")})


def gen_del(tokens: list[TokenInfo]) -> bool:
    return len(tokens) >= 2 and tokens[0].text == "GEN" and tokens[1].text in {"DEL", "DEL."}


def postal_box_line(tokens: list[TokenInfo]) -> bool:
    return bool(tokens) and (
        tokens[0].text in PO_BOX_KEYWORDS
        or (len(tokens) >= 2 and tokens[0].text == "CASE" and tokens[1].text == "POSTALE")
    )


def rural_route_line(tokens: list[TokenInfo]) -> bool:
    return bool(tokens) and (
        tokens[0].text in {"RR", "R.R."}
        or (
            len(tokens) >= 2
            and tokens[0].text.removesuffix(".") == "R"
            and tokens[1].text.removesuffix(".") == "R"
        )
    )


def find_postal_code(line: LineTokens) -> str | None:
    for i, token in enumerate(line.tokens):
        if token.type == P.POSTAL_CODE:
            return format_postal_code(token.text)
        if is_fsa(token.text) and i + 1 < len(line.tokens) and is_ldu(line.tokens[i + 1].text):
            return token.text + " " + line.tokens[i + 1].text
    return None


def postal_code_only(line: LineTokens) -> bool:
    tokens = [t for t in line.tokens if t.type not in {P.COMMA, P.DOT}]
    if len(tokens) == 1:
        return tokens[0].type == P.POSTAL_CODE
    if len(tokens) in {2, 3} and is_fsa(tokens[0].text):
        return is_ldu("".join(t.text for t in tokens[1:]))
    return False


def country_line(line: LineTokens) -> bool:
    return len(line.tokens) == 1 and line.tokens[0].text in COUNTRY_KEYWORDS


def unit_hyphen_civic(tokens: list[TokenInfo]) -> bool:
    return (
        len(tokens) >= 3
        and tokens[1].type == P.HYPHEN
        and tokens[2].type == P.NUMBER
        and (
            tokens[0].type in NUMERIC_TYPES
            or (
                tokens[0].type == P.WORD
                and (
                    tokens[0].text == "PH"
                    or (len(tokens[0].text) == 1 and tokens[0].text.isalpha())
                )
            )
        )
    )


def looks_like_delivery(tokens: list[TokenInfo]) -> bool:
    if not tokens:
        return False
    first = tokens[0]
    if first.type in NUMERIC_TYPES or unit_hyphen_civic(tokens):
        return True
    if first.type == P.LPAREN and len(tokens) >= 2 and tokens[1].type in NUMERIC_TYPES:
        return True
    if (first.text in {"GD", "GENERAL"} or postal_box_line(tokens)
            or rural_route_line(tokens) or gen_del(tokens)
            or is_unit_designator(first.text) or unit_prefix(first.text)):
        return True
    return len(tokens) >= 2 and first.text in PREFIX_DIRECTIONS and tokens[1].type in NUMERIC_TYPES


def collect_street_words(tokens: list[TokenInfo], start: int) -> list[str]:
    words: list[str] = []
    i = start
    while i < len(tokens):
        token = tokens[i]
        if token.type in WORD_TYPES:
            if words and i >= 2:
                separator = tokens[i - 1]
                if (separator.type in {P.HYPHEN, P.SLASH}
                        and adjacent(tokens[i - 2], separator) and adjacent(separator, token)):
                    words[-1] += separator.text + token.text
                    i += 1
                    continue
            words.append(token.text)
        elif token.type == P.AMPERSAND:
            words.append("&")
        elif token.type == P.LPAREN:
            paren = "("
            for j in range(i + 1, len(tokens)):
                part = tokens[j]
                if part.type == P.RPAREN:
                    paren += ")"
                    i = j
                    break
                if part.type in WORD_TYPES | {P.HYPHEN}:
                    if len(paren) > 1:
                        paren += "-" if part.type == P.HYPHEN else " "
                    paren += part.text
            if words:
                words[-1] += " " + paren
            else:
                words.append(paren)
        elif token.type == P.HYPHEN and words:
            previous = i > 0 and adjacent(tokens[i - 1], token)
            following = i + 1 < len(tokens) and adjacent(token, tokens[i + 1])
            if not previous and not following:
                words.append("-")
        i += 1
    return words


class AddressComponentVisitor(CanadianAddressVisitor):
    def __init__(
        self, parsing_mode: ParsingMode = ParsingMode.LENIENT,
        *, has_addressee: bool = True, has_postal_code: bool = True,
    ):
        self.has_addressee = has_addressee
        self.has_postal_code = has_postal_code
        self.components = AddressComponents(parsing_mode=parsing_mode)

    def _set(self, **fields) -> None:
        self.components = replace(self.components, **fields)

    def _set_postal_code(self, postal_code: str) -> None:
        if self.has_postal_code:
            self._set(postal_code=postal_code)

    def visitAddress(self, ctx: P.AddressContext) -> AddressComponents:
        body = ctx.addressBody()
        if body is None:
            return self.components
        lines = []
        for line in body.addressLine():
            tokens = [
                TokenInfo(t.start.type, t.getText().upper(), t.start.start, t.stop.stop)
                for t in line.lineContent().lineToken()
            ]
            if tokens:
                lines.append(line_from_tokens(tokens))
        if not lines:
            return self.components
        comma_split = False
        if len(lines) == 1:
            split = self._comma_split(lines[0])
            if split:
                lines = split
                comma_split = True
        if len(lines) == 1:
            if comma_split:
                self._region(lines[0])
            elif len(lines[0].tokens) <= 3 and (pc := find_postal_code(lines[0])):
                self._set_postal_code(pc)
            elif self.has_addressee:
                self._set(addressee=lines[0].text)
            else:
                self._delivery(lines[0])
        elif comma_split:
            self._region(lines[-1])
            for line in lines[:-1]:
                self._delivery(line)
        else:
            region = len(lines) - 1
            if country_line(lines[region]):
                self._set(country=lines[region].text)
                region -= 1
            if region >= 1 and postal_code_only(lines[region]):
                if pc := find_postal_code(lines[region]):
                    self._set_postal_code(pc)
                    region -= 1
            self._region(lines[region])
            addressee_seen = False
            for line in lines[:region]:
                tokens = line.tokens
                if country_line(line):
                    self._set(country=line.text)
                elif postal_code_only(line):
                    if pc := find_postal_code(line):
                        self._set_postal_code(pc)
                elif care_of(tokens):
                    self._set(care_of=" ".join(t.text for t in tokens[3:]))
                elif tokens[0].text in {"SITE", "COMP", "EMPL"}:
                    self._site(line)
                elif looks_like_delivery(tokens):
                    self._delivery(line)
                elif self.has_addressee and not addressee_seen:
                    self._set(addressee=line.text)
                    addressee_seen = True
                else:
                    self._delivery(line)
        if not self.components.province and self.components.postal_code:
            inferred = POSTAL_PREFIX_TO_PROVINCE.get(self.components.postal_code[0])
            if inferred:
                self._set(province=inferred)
        if self.components.address_type == AddressType.INCOMPLETE:
            civic = bool(self.components.street_name or self.components.street_number)
            box = bool(self.components.postal_box_number)
            if box and civic:
                kind = AddressType.MIXED
            elif box:
                kind = AddressType.POSTAL_BOX
            elif self.components.rural_route:
                kind = AddressType.RURAL_ROUTE
            elif civic:
                kind = AddressType.CIVIC
            else:
                kind = AddressType.INCOMPLETE
            self._set(address_type=kind)
        self._set(is_complete=self.components.is_mailing_valid())
        return self.components

    def _comma_split(self, line: LineTokens) -> list[LineTokens] | None:
        tokens = line.tokens
        for i, token in enumerate(tokens):
            if token.type != P.COMMA:
                continue
            after = i + 1
            while after < len(tokens) and tokens[after].type == P.COMMA:
                after += 1
            marker = any(
                (t.type == P.WORD and (
                    t.text in PROVINCE_CODES or t.text in PROVINCE_NAMES
                    or t.text.removesuffix(".") in PROVINCE_NAMES))
                or t.type == P.POSTAL_CODE or is_fsa(t.text)
                for t in tokens[after:] if t.type != P.COMMA
            )
            if not marker or not any(t.type in WORD_TYPES for t in tokens[:i]):
                continue
            delivery, region = tokens[:i], tokens[after:]
            if not delivery or not region:
                return None
            if not any(t.type in NUMERIC_TYPES for t in delivery):
                return [line_from_tokens(delivery + region)]
            return [line_from_tokens(delivery), line_from_tokens(region)]
        return None

    def _region(self, line: LineTokens) -> None:
        tokens = line.tokens
        pc_start = pc_end = -1
        if not self.components.postal_code:
            for i, token in enumerate(tokens):
                pc = None
                if token.type == P.POSTAL_CODE:
                    pc, pc_start, pc_end = format_postal_code(token.text), i, i
                elif is_fsa(token.text) and i + 1 < len(tokens):
                    if is_ldu(tokens[i + 1].text):
                        pc, pc_start, pc_end = token.text + " " + tokens[i + 1].text, i, i + 1
                    elif i + 2 < len(tokens) and is_ldu(tokens[i + 1].text + tokens[i + 2].text):
                        pc = token.text + " " + tokens[i + 1].text + tokens[i + 2].text
                        pc_start, pc_end = i, i + 2
                if pc:
                    self._set_postal_code(pc)
                    break
        province = None
        province_start = province_end = -1
        for i, token in enumerate(tokens):
            if pc_start <= i <= pc_end:
                continue
            if token.type == P.WORD and token.text in PROVINCE_CODES:
                province, province_start, province_end = token.text, i, i
                break
        if province is None:
            scan_end = pc_start if pc_start >= 0 else len(tokens)
            for i in range(scan_end - 1, -1, -1):
                if tokens[i].type != P.WORD:
                    continue
                for name, code in PROVINCE_NAMES.items():
                    parts = name.split()
                    start = i - len(parts) + 1
                    if start < 0:
                        continue
                    if all(
                        not (pc_start >= 0 and pc_start <= start + j <= pc_end)
                        and tokens[start + j].text.removesuffix(".") == part.removesuffix(".")
                        for j, part in enumerate(parts)
                    ):
                        province, province_start, province_end = code, start, i
                        break
                if province:
                    break
        if province:
            self._set(province=province)
        country = -1
        for i in range(len(tokens) - 1, -1, -1):
            if pc_start <= i <= pc_end or province_start <= i <= province_end:
                continue
            if tokens[i].type == P.WORD and tokens[i].text in COUNTRY_KEYWORDS:
                self._set(country=tokens[i].text)
                country = i
                break
        end = min([len(tokens)] + [i for i in (pc_start, province_start, country) if i >= 0])
        municipality = " ".join(t.text for t in tokens[:end] if t.type != P.COMMA).strip()
        if municipality:
            self._set(municipality=municipality)

    def _delivery(self, line: LineTokens) -> None:
        tokens = line.tokens
        if not tokens:
            return
        first = tokens[0]
        if care_of(tokens):
            self._set(care_of=" ".join(t.text for t in tokens[3:]))
        elif postal_box_line(tokens):
            self._set(address_type=AddressType.POSTAL_BOX)
            for token in tokens:
                if token.type == P.NUMBER:
                    self._set(postal_box_number=token.text)
                    break
        elif rural_route_line(tokens):
            self._set(address_type=AddressType.RURAL_ROUTE)
            for token in tokens[1:]:
                if token.type == P.NUMBER:
                    self._set(rural_route=token.text)
                    break
        elif first.text in {"GD", "GENERAL"} or gen_del(tokens):
            self._set(address_type=AddressType.GENERAL_DELIVERY)
        elif first.text in {"SITE", "COMP", "EMPL"}:
            self._site(line)
        elif (
            not self.has_addressee
            and len(tokens) >= 3
            and first.type == P.WORD
            and tokens[-2].type == P.COMMA
            and tokens[-1].type == P.NUMBER
            and all(t.type in WORD_TYPES for t in tokens[:-2])
        ):
            self._set(
                street_number=tokens[-1].text,
                street_name=" ".join(collect_street_words(tokens[:-2], 0)),
                street_type="", street_direction="", address_type=AddressType.CIVIC,
            )
        elif unit_hyphen_civic(tokens):
            self._civic(tokens)
        elif is_unit_designator(first.text):
            self._leading_unit(tokens)
        elif split := unit_prefix(first.text):
            self._set(unit_number=split[1])
            idx = 1
            if idx < len(tokens) and tokens[idx].type in NUMERIC_TYPES:
                self._set(street_number=tokens[idx].text, address_type=AddressType.CIVIC)
                idx = self._post_civic(tokens, idx + 1)
            self._street(tokens, idx)
        elif first.type in NUMERIC_TYPES:
            self._civic(tokens)
        elif first.type == P.LPAREN and len(tokens) >= 2 and tokens[1].type in NUMERIC_TYPES:
            self._civic([t for t in tokens if t.type not in {P.LPAREN, P.RPAREN}])
        elif any(t.type == P.WORD and normalize_street_type(t.text) for t in tokens):
            self._street(tokens, 0, street_only=True)
            if self.components.address_type == AddressType.INCOMPLETE:
                self._set(address_type=AddressType.CIVIC)
        elif not self.components.care_of:
            self._set(care_of=line.text)

    def _site(self, line: LineTokens) -> None:
        tokens = line.tokens
        for idx in range(1, len(tokens)):
            suffix = tokens[idx:]
            if rural_route_line(suffix):
                prefix_length = 1 if suffix[0].text in {"RR", "R.R."} else 2
                if len(suffix) > prefix_length and suffix[prefix_length].type == P.NUMBER:
                    self._set(site_info=line_from_tokens(tokens[:idx]).text)
                    self._delivery(line_from_tokens(suffix))
                    return
        self._set(site_info=line.text)

    def _civic(self, tokens: list[TokenInfo]) -> None:
        if unit_hyphen_civic(tokens):
            self._set(unit_number=tokens[0].text, street_number=tokens[2].text)
            idx = 3
        else:
            self._set(street_number=tokens[0].text)
            idx = 1
        self._set(address_type=AddressType.CIVIC)
        self._street(tokens, self._post_civic(tokens, idx))

    def _post_civic(self, tokens: list[TokenInfo], idx: int) -> int:
        if (idx + 2 < len(tokens) and tokens[idx].type == P.NUMBER
                and tokens[idx + 1].type == P.SLASH and tokens[idx + 2].type == P.NUMBER):
            numerator, denominator = tokens[idx].text, tokens[idx + 2].text
            numeric_numerator = numerator.lstrip("0") or "0"
            numeric_denominator = denominator.lstrip("0") or "0"
            # Java's fraction recognizer rejects values outside a signed 32-bit integer.
            if (len(numeric_numerator) <= 10 and numeric_denominator in {"2", "3", "4", "8"}
                    and int(numeric_numerator) <= 2147483647
                    and int(numeric_numerator) < int(numeric_denominator)):
                self._set(street_number=self.components.street_number + " " + numerator + "/" + denominator)
                return idx + 3
        if idx + 1 < len(tokens) and tokens[idx].type == P.SLASH and tokens[idx + 1].type == P.NUMBER:
            self._set(street_number=self.components.street_number + "/" + tokens[idx + 1].text)
            return idx + 2
        return idx

    def _leading_unit(self, tokens: list[TokenInfo]) -> None:
        idx = 1
        if idx >= len(tokens):
            return
        if (idx + 3 < len(tokens) and tokens[idx].type == P.NUMBER
                and tokens[idx + 1].type in {P.SLASH, P.HYPHEN, P.DOT}
                and tokens[idx + 2].type == P.NUMBER
                and adjacent(tokens[idx], tokens[idx + 1])
                and adjacent(tokens[idx + 1], tokens[idx + 2])
                and tokens[idx + 3].type in NUMERIC_TYPES):
            self._set(unit_number="".join(t.text for t in tokens[idx:idx + 3]))
            idx += 3
        if idx == 1:
            candidate = tokens[idx]
            following_civic = idx + 1 < len(tokens) and tokens[idx + 1].type in NUMERIC_TYPES
            if candidate.type == P.ALPHANUMERIC or (candidate.type in {P.WORD, P.NUMBER} and following_civic):
                self._set(unit_number=candidate.text)
                idx += 1
        if idx < len(tokens) and tokens[idx].type in NUMERIC_TYPES:
            self._set(street_number=tokens[idx].text, address_type=AddressType.CIVIC)
            idx = self._post_civic(tokens, idx + 1)
        self._street(tokens, idx)

    def _street(self, tokens: list[TokenInfo], start: int, street_only: bool = False) -> None:
        words = collect_street_words(tokens, start)
        if not words:
            return
        direction = None
        prefix_set = DIRECTIONS if street_only else PREFIX_DIRECTIONS
        prefix = len(words) > 1 and words[0] in prefix_set
        if len(words) > 1 and words[-1] in DIRECTIONS:
            bare_french = len(words) == 2 and len(words[-1]) == 1 and is_french_street_type(words[0])
            if not bare_french and not opposing_directions(words[-2], words[-1]):
                direction = words.pop()
        if direction is None and prefix:
            direction = words.pop(0)
        if not street_only and not self.components.unit_number and len(words) >= 3:
            self._trailing_unit(words)
        self._classify_street(words)
        if direction is not None and not self.components.street_direction:
            self._set(street_direction=direction)

    def _trailing_unit(self, words: list[str]) -> None:
        type_idx = next((i for i in range(len(words) - 1, 0, -1)
                         if normalize_street_type(words[i])), -1)
        if type_idx < 0 or type_idx == len(words) - 1:
            return
        after = type_idx + 1
        direction = None
        if words[after] in DIRECTIONS:
            direction = words[after]
            after += 1
        unit = None
        if after + 1 < len(words) and is_unit_designator(words[after]):
            unit = " ".join(words[after + 1:])
        elif after < len(words) and (split := unit_prefix(words[after])):
            unit = split[1]
        if unit is not None:
            self._set(unit_number=unit)
            if direction:
                self._set(street_direction=direction)
            del words[type_idx + 1 if direction else after:]

    def _classify_street(self, words: list[str]) -> None:
        if not words:
            return
        if len(words) == 1:
            self._set(street_name=words[0])
            return
        first = words[0]
        if is_french_street_type(first):
            name_words = words[1:]
            stripped = name_words[0].removesuffix(".")
            type_as_name = (len(name_words) == 1 and normalize_street_type(stripped)
                            and stripped not in TYPE_WORDS_USED_AS_NAMES)
            if not type_as_name:
                bare = (len(name_words[0]) == 1 and name_words[0].isalpha()
                        or re.fullmatch(r"\d{1,2}[A-Za-z]?", name_words[0]) is not None)
                keep_avenue = first.removesuffix(".") in {"AVENUE", "AVE", "AV"} and len(name_words) == 1 and bare
                self._set(street_type=normalize_street_type(first) or first,
                          street_name=" ".join(words if keep_avenue else name_words))
                return
        last = normalize_street_type(words[-1])
        preceding = words[-2].removesuffix(".")
        if (last is None or preceding == "THE"
                or (preceding in {"GRAND", "GRANDE", "LE", "LA", "LES"}
                    and is_french_street_type(words[-1])
                    and (last not in STREET_TYPES_EN or self.components.province == "QC"))):
            self._set(street_name=" ".join(words))
        elif len(words) >= 3 and len(preceding) <= 2 and preceding in DIRECTIONS:
            self._set(street_type=last, street_direction=words[-2], street_name=" ".join(words[:-2]))
        else:
            self._set(street_type=last, street_name=" ".join(words[:-1]))
