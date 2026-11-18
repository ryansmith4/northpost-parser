"""Immutable parsed address components and parsing/normalization strategies."""

from dataclasses import dataclass, fields, replace
from enum import Enum


class AddressType(Enum):
    CIVIC = "CIVIC"
    POSTAL_BOX = "POSTAL_BOX"
    RURAL_ROUTE = "RURAL_ROUTE"
    GENERAL_DELIVERY = "GENERAL_DELIVERY"
    MIXED = "MIXED"
    INCOMPLETE = "INCOMPLETE"


class ParsingMode(Enum):
    STRICT = "STRICT"
    LENIENT = "LENIENT"


class ParsingStrategy(Enum):
    LENIENT = "LENIENT"
    STRICT = "STRICT"
    STRICT_THEN_LENIENT = "STRICT_THEN_LENIENT"


class NormalizationStrategy(Enum):
    FULL_FORM = "FULL_FORM"
    ABBREVIATED = "ABBREVIATED"


@dataclass(frozen=True)
class AddressComponents:
    addressee: str = ""
    care_of: str = ""
    unit_number: str = ""
    street_number: str = ""
    street_name: str = ""
    street_type: str = ""
    street_direction: str = ""
    postal_box_number: str = ""
    rural_route: str = ""
    site_info: str = ""
    municipality: str = ""
    province: str = ""
    postal_code: str = ""
    country: str = ""
    address_type: AddressType = AddressType.INCOMPLETE
    parsing_mode: ParsingMode = ParsingMode.LENIENT
    is_complete: bool = False

    def __post_init__(self) -> None:
        for field in fields(self):
            if field.name not in {"address_type", "parsing_mode", "is_complete"}:
                value = getattr(self, field.name)
                object.__setattr__(self, field.name, "" if value is None else value.strip().upper())
        if self.address_type is None:
            raise TypeError("Address type cannot be null")
        if self.parsing_mode is None:
            raise TypeError("Parsing mode cannot be null")

    def has_addressee(self) -> bool:
        return bool(self.addressee)

    def has_care_of(self) -> bool:
        return bool(self.care_of)

    def has_unit_number(self) -> bool:
        return bool(self.unit_number)

    def has_street_number(self) -> bool:
        return bool(self.street_number)

    def has_street_name(self) -> bool:
        return bool(self.street_name)

    def has_street_type(self) -> bool:
        return bool(self.street_type)

    def has_street_direction(self) -> bool:
        return bool(self.street_direction)

    def has_postal_box_number(self) -> bool:
        return bool(self.postal_box_number)

    def has_rural_route(self) -> bool:
        return bool(self.rural_route)

    def has_municipality(self) -> bool:
        return bool(self.municipality)

    def has_province(self) -> bool:
        return bool(self.province)

    def has_postal_code(self) -> bool:
        return bool(self.postal_code)

    def has_country(self) -> bool:
        return bool(self.country)

    def get_formatted_street_address(self) -> str | None:
        value = f"{self.unit_number}-" if self.unit_number else ""
        if self.street_number:
            value += self.street_number + " "
        if self.street_name:
            value += " ".join(filter(None, (self.street_name, self.street_type, self.street_direction)))
        return value.strip() or None

    def get_full_street_name(self) -> str | None:
        if not self.street_name:
            return None
        return " ".join(filter(None, (self.street_name, self.street_type, self.street_direction)))

    def get_formatted_civic_line(self) -> str | None:
        match self.address_type:
            case AddressType.CIVIC:
                return self.get_formatted_street_address()
            case AddressType.POSTAL_BOX:
                return f"PO BOX {self.postal_box_number}" if self.postal_box_number else None
            case AddressType.RURAL_ROUTE:
                return f"RR {self.rural_route}" if self.rural_route else None
            case AddressType.GENERAL_DELIVERY:
                return "GENERAL DELIVERY"
            case _:
                return None

    def get_formatted_last_line(self) -> str | None:
        value = " ".join(filter(None, (self.municipality, self.province)))
        if self.postal_code:
            value += ("  " if value else "") + self.postal_code
        return value or None

    def get_formatted_address(self) -> str:
        return "\n".join(filter(None, (
            self.addressee,
            f"C/O {self.care_of}" if self.care_of else "",
            self.get_formatted_civic_line(),
            self.get_formatted_last_line(),
            self.country,
        ))).strip()

    def normalize(self, strategy: NormalizationStrategy) -> "AddressComponents":
        from .normalizer import AddressNormalizer

        return replace(
            self,
            street_type=AddressNormalizer.normalize_street_type(self.street_type, strategy),
            street_direction=AddressNormalizer.normalize_direction(self.street_direction, strategy),
        )

    def is_mailing_valid(self) -> bool:
        return bool(
            self.addressee
            and (self.street_name or self.postal_box_number or self.rural_route)
            and self.municipality
            and self.province
            and self.postal_code
        )
