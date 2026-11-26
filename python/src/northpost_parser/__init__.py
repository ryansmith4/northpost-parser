"""Canadian postal address parsing."""

from .models import (
    AddressComponents,
    AddressType,
    NormalizationStrategy,
    ParsingMode,
    ParsingStrategy,
)
from .normalizer import AddressNormalizer
from .service import AddressParserService, BatchStatistics, ParsingResult

__all__ = [
    "AddressComponents",
    "AddressNormalizer",
    "AddressParserService",
    "AddressType",
    "BatchStatistics",
    "NormalizationStrategy",
    "ParsingMode",
    "ParsingResult",
    "ParsingStrategy",
]
