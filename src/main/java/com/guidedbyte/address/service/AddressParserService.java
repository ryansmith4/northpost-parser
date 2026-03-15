package com.guidedbyte.address.service;

import com.guidedbyte.address.model.AddressComponents;
import com.guidedbyte.address.model.AddressComponents.ParsingMode;
import com.guidedbyte.address.parser.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for parsing Canadian postal addresses using the grammar.
 *
 * <p>The v3 grammar is minimal — the lexer produces only structural tokens (WORD, NUMBER, POSTAL_CODE, etc.) and all
 * semantic interpretation is in the visitor. Input is normalized to uppercase before parsing.
 *
 * <p>Supports three parsing strategies: - LENIENT: always succeeds, extracts whatever components are available -
 * STRICT: fails if the address is not complete for mailing purposes - STRICT_THEN_LENIENT: tries strict first, falls
 * back to lenient
 */
public class AddressParserService {

    private static final Logger logger = LoggerFactory.getLogger(AddressParserService.class);

    /** Parsing strategy controls how incomplete addresses are handled. */
    public enum ParsingStrategy {
        /** Always succeeds — extracts whatever components are available */
        LENIENT,
        /** Fails if the address is not complete for mailing purposes */
        STRICT,
        /** Tries strict first, falls back to lenient on failure */
        STRICT_THEN_LENIENT
    }

    /** Result container for parsing operations */
    public record ParsingResult(
            AddressComponents components,
            boolean successful,
            List<String> errors,
            List<String> warnings,
            ParsingMode modeUsed) {
        public ParsingResult {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /** Custom error listener that collects ANTLR errors instead of printing to stderr */
    private static class CollectingErrorListener extends BaseErrorListener {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
            errors.add("Line " + line + ":" + charPositionInLine + " " + msg);
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
    }

    /** Parse a single address string using lenient strategy (always succeeds). */
    public ParsingResult parseAddress(String addressText) {
        return parseAddress(addressText, ParsingStrategy.LENIENT);
    }

    /** Parse a single address string using the specified strategy. */
    public ParsingResult parseAddress(String addressText, ParsingStrategy strategy) {
        if (addressText == null || addressText.trim().isEmpty()) {
            return new ParsingResult(
                    AddressComponents.builder().build(),
                    false,
                    List.of("Address text cannot be null or empty"),
                    List.of(),
                    ParsingMode.LENIENT);
        }

        logger.debug("Parsing address with strategy {}: {}", strategy, addressText);

        // Normalize: trim and uppercase before feeding to the lexer
        String normalized = addressText.trim().toUpperCase();

        return switch (strategy) {
            case LENIENT -> doParse(normalized, ParsingMode.LENIENT);
            case STRICT -> doParse(normalized, ParsingMode.STRICT);
            case STRICT_THEN_LENIENT -> {
                ParsingResult strict = doParse(normalized, ParsingMode.STRICT);
                if (strict.successful()) {
                    yield strict;
                }
                logger.debug("Strict parsing rejected address, falling back to lenient");
                ParsingResult lenient = doParse(normalized, ParsingMode.LENIENT);
                // Carry forward the strict rejection as a warning
                List<String> combinedWarnings = new ArrayList<>(lenient.warnings());
                combinedWarnings.add("Strict parsing failed: " + String.join("; ", strict.errors()));
                yield new ParsingResult(
                        lenient.components(),
                        lenient.successful(),
                        lenient.errors(),
                        combinedWarnings,
                        ParsingMode.LENIENT);
            }
        };
    }

    private ParsingResult doParse(String addressText, ParsingMode mode) {
        CollectingErrorListener errorListener = new CollectingErrorListener();
        List<String> warnings = new ArrayList<>();

        try {
            // Create lexer and parser
            CanadianAddressLexer lexer = new CanadianAddressLexer(CharStreams.fromString(addressText));
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CanadianAddressParser parser = new CanadianAddressParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            // Parse the address
            ParseTree tree = parser.address();

            // Check for parsing errors
            if (!errorListener.getErrors().isEmpty()) {
                return new ParsingResult(
                        AddressComponents.builder().parsingMode(mode).build(),
                        false,
                        errorListener.getErrors(),
                        warnings,
                        mode);
            }

            // Extract components using the v3 visitor
            AddressComponentVisitor visitor = new AddressComponentVisitor(mode);
            AddressComponents components = visitor.visit(tree);

            // Validate and add warnings
            warnings.addAll(validateComponents(components));

            // In strict mode, reject addresses that aren't complete for mailing
            if (mode == ParsingMode.STRICT && !components.isMailingValid()) {
                return new ParsingResult(components, false, buildStrictErrors(components), warnings, mode);
            }

            return new ParsingResult(components, true, List.of(), warnings, mode);

        } catch (Exception e) {
            logger.error("Unexpected error during parsing", e);
            return new ParsingResult(
                    AddressComponents.builder().parsingMode(mode).build(),
                    false,
                    List.of("Unexpected error: " + e.getMessage()),
                    warnings,
                    mode);
        }
    }

    private List<String> buildStrictErrors(AddressComponents components) {
        List<String> errors = new ArrayList<>();
        errors.add("Address is not complete for mailing purposes");
        if (!components.hasAddressee()) errors.add("Missing addressee");
        if (!components.hasStreetNumber() && !components.hasPostalBoxNumber() && !components.hasRuralRoute())
            errors.add("Missing delivery address");
        if (!components.hasMunicipality()) errors.add("Missing municipality");
        if (!components.hasProvince()) errors.add("Missing province");
        if (!components.hasPostalCode()) errors.add("Missing postal code");
        return errors;
    }

    /** Validate components and generate warnings */
    private List<String> validateComponents(AddressComponents components) {
        List<String> warnings = new ArrayList<>();

        // Redundant country
        if (components.hasCountry()) {
            warnings.add("Country specification is redundant for Canadian addresses");
        }

        // Incomplete for mailing
        if (!components.isMailingValid()) {
            warnings.add("Address may be incomplete for mailing purposes");
        }

        // Street name without number
        if (components.hasStreetName()
                && !components.hasStreetNumber()
                && !components.hasPostalBoxNumber()
                && !components.hasRuralRoute()) {
            warnings.add("Street name provided without street number");
        }

        // Postal code format
        if (components.hasPostalCode()) {
            String pc = components.postalCode().replaceAll("\\s", "");
            if (pc.length() != 6) {
                warnings.add("Postal code format may be invalid");
            }
        }

        // Mixed address types
        int typeCount = 0;
        if (components.hasStreetName()) typeCount++;
        if (components.hasPostalBoxNumber()) typeCount++;
        if (components.hasRuralRoute()) typeCount++;
        if (typeCount > 1) {
            warnings.add("Multiple address types detected - this may be unusual");
        }

        return warnings;
    }

    /** Batch parsing for multiple addresses (lenient). */
    public List<ParsingResult> parseAddresses(List<String> addresses) {
        return parseAddresses(addresses, ParsingStrategy.LENIENT);
    }

    /** Batch parsing for multiple addresses with the specified strategy. */
    public List<ParsingResult> parseAddresses(List<String> addresses, ParsingStrategy strategy) {
        return addresses.stream().map(addr -> parseAddress(addr, strategy)).toList();
    }

    /** Batch statistics */
    public record BatchStatistics(
            int totalAddresses,
            int successfulParses,
            int strictModeUsed,
            int lenientModeUsed,
            int completeAddresses,
            List<String> commonWarnings) {}

    public BatchStatistics getBatchStatistics(List<ParsingResult> results) {
        int total = results.size();
        int successful =
                (int) results.stream().filter(ParsingResult::successful).count();
        int strictUsed = (int)
                results.stream().filter(r -> r.modeUsed() == ParsingMode.STRICT).count();
        int lenientUsed = (int) results.stream()
                .filter(r -> r.modeUsed() == ParsingMode.LENIENT)
                .count();
        int complete =
                (int) results.stream().filter(r -> r.components().isComplete()).count();

        var warningCounts = results.stream()
                .flatMap(r -> r.warnings().stream())
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        List<String> commonWarnings = warningCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > total * 0.1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        return new BatchStatistics(total, successful, strictUsed, lenientUsed, complete, commonWarnings);
    }
}
