"""ANTLR-backed parsing service with strict and lenient validation."""

from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass
import logging
import re

from antlr4 import CommonTokenStream, InputStream
from antlr4.error.ErrorListener import ErrorListener

from .generated.CanadianAddressLexer import CanadianAddressLexer
from .generated.CanadianAddressParser import CanadianAddressParser
from .models import AddressComponents, ParsingMode, ParsingStrategy
from .visitor import AddressComponentVisitor

_logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ParsingResult:
    components: AddressComponents
    successful: bool
    errors: tuple[str, ...]
    warnings: tuple[str, ...]
    mode_used: ParsingMode

    def __post_init__(self) -> None:
        object.__setattr__(self, "errors", tuple(self.errors))
        object.__setattr__(self, "warnings", tuple(self.warnings))

    def has_errors(self) -> bool:
        return bool(self.errors)


@dataclass(frozen=True)
class BatchStatistics:
    total_addresses: int
    successful_parses: int
    strict_mode_used: int
    lenient_mode_used: int
    complete_addresses: int
    common_warnings: tuple[str, ...]


class _CollectingErrorListener(ErrorListener):
    def __init__(self) -> None:
        super().__init__()
        self.errors: list[str] = []

    def syntaxError(self, recognizer, offendingSymbol, line, column, msg, e):
        self.errors.append(f"Line {line}:{column} {msg}")


class AddressParserService:
    def parse_address(
        self, address_text: str | None, strategy: ParsingStrategy = ParsingStrategy.LENIENT,
        *, has_addressee: bool = True, has_postal_code: bool = True,
    ) -> ParsingResult:
        if address_text is None or not address_text.strip():
            return ParsingResult(
                AddressComponents(), False, ("Address text cannot be null or empty",),
                (), ParsingMode.LENIENT,
            )
        normalized = re.sub(r"[ \t]+", " ", address_text.strip()).upper()
        match strategy:
            case ParsingStrategy.LENIENT:
                return self._do_parse(normalized, ParsingMode.LENIENT, has_addressee, has_postal_code)
            case ParsingStrategy.STRICT:
                return self._do_parse(normalized, ParsingMode.STRICT, has_addressee, has_postal_code)
            case ParsingStrategy.STRICT_THEN_LENIENT:
                strict = self._do_parse(normalized, ParsingMode.STRICT, has_addressee, has_postal_code)
                if strict.successful:
                    return strict
                lenient = self._do_parse(normalized, ParsingMode.LENIENT, has_addressee, has_postal_code)
                return ParsingResult(
                    lenient.components, lenient.successful, lenient.errors,
                    lenient.warnings + ("Strict parsing failed: " + "; ".join(strict.errors),),
                    ParsingMode.LENIENT,
                )
            case _:
                raise ValueError(f"Unknown parsing strategy: {strategy!r}")

    def _do_parse(
        self, address_text: str, mode: ParsingMode, has_addressee: bool, has_postal_code: bool
    ) -> ParsingResult:
        listener = _CollectingErrorListener()
        warnings: list[str] = []
        try:
            lexer = CanadianAddressLexer(InputStream(address_text))
            lexer.removeErrorListeners()
            lexer.addErrorListener(listener)
            parser = CanadianAddressParser(CommonTokenStream(lexer))
            parser.removeErrorListeners()
            parser.addErrorListener(listener)
            tree = parser.address()
            if listener.errors:
                return ParsingResult(
                    AddressComponents(parsing_mode=mode), False, tuple(listener.errors), (), mode,
                )
            components = AddressComponentVisitor(
                mode, has_addressee=has_addressee, has_postal_code=has_postal_code
            ).visit(tree)
            warnings.extend(self._validate_components(
                components, has_addressee=has_addressee, has_postal_code=has_postal_code
            ))
            if mode is ParsingMode.STRICT and not components.is_mailing_valid():
                return ParsingResult(
                    components, False, tuple(self._build_strict_errors(components)),
                    tuple(warnings), mode,
                )
            return ParsingResult(components, True, (), tuple(warnings), mode)
        except Exception as error:
            _logger.exception("Unexpected error during parsing")
            return ParsingResult(
                AddressComponents(parsing_mode=mode), False,
                (f"Unexpected error: {error}",), tuple(warnings), mode,
            )

    @staticmethod
    def _build_strict_errors(components: AddressComponents) -> list[str]:
        errors = ["Address is not complete for mailing purposes"]
        if not components.addressee:
            errors.append("Missing addressee")
        if not (components.street_number or components.postal_box_number or components.rural_route):
            errors.append("Missing delivery address")
        if not components.municipality:
            errors.append("Missing municipality")
        if not components.province:
            errors.append("Missing province")
        if not components.postal_code:
            errors.append("Missing postal code")
        return errors

    @staticmethod
    def _validate_components(
        components: AddressComponents, *, has_addressee: bool = True, has_postal_code: bool = True
    ) -> list[str]:
        warnings = []
        if components.country:
            warnings.append("Country specification is redundant for Canadian addresses")
        if not (
            (not has_addressee or components.addressee)
            and (components.street_name or components.postal_box_number or components.rural_route)
            and components.municipality
            and components.province
            and (not has_postal_code or components.postal_code)
        ):
            warnings.append("Address may be incomplete for mailing purposes")
        if (components.street_name and not components.street_number
                and not components.postal_box_number and not components.rural_route):
            warnings.append("Street name provided without street number")
        if components.postal_code and len(re.sub(r"\s", "", components.postal_code)) != 6:
            warnings.append("Postal code format may be invalid")
        if sum(bool(value) for value in (
            components.street_name, components.postal_box_number, components.rural_route,
        )) > 1:
            warnings.append("Multiple address types detected - this may be unusual")
        return warnings

    def parse_addresses(
        self, addresses: Iterable[str | None], strategy: ParsingStrategy = ParsingStrategy.LENIENT,
        *, has_addressee: bool = True, has_postal_code: bool = True,
    ) -> list[ParsingResult]:
        return [
            self.parse_address(
                address, strategy, has_addressee=has_addressee, has_postal_code=has_postal_code
            )
            for address in addresses
        ]

    def get_batch_statistics(self, results: Iterable[ParsingResult]) -> BatchStatistics:
        results = list(results)
        total = len(results)
        counts = Counter(warning for result in results for warning in result.warnings)
        return BatchStatistics(
            total,
            sum(result.successful for result in results),
            sum(result.mode_used is ParsingMode.STRICT for result in results),
            sum(result.mode_used is ParsingMode.LENIENT for result in results),
            sum(result.components.is_complete for result in results),
            tuple(sorted(warning for warning, count in counts.items() if count > total * 0.1)),
        )
