# Contributing to NorthPost Parser

Thank you for your interest in contributing! Here's how to get started.

## Development Setup

1. **Java 21** or later
2. Clone the repo and build:
   ```bash
   ./gradlew build
   ```

## Making Changes

1. Fork the repository and create a feature branch from `main`
2. Make your changes
3. Ensure all tests pass: `./gradlew build`
4. Submit a pull request

## Code Style

- 4-space indentation (no tabs)
- Follow existing conventions in the codebase
- An `.editorconfig` file is included for editor support

## Grammar Changes

If your change involves the ANTLR grammar (`src/main/antlr/CanadianAddress.g4`), keep in mind:
- The grammar is deliberately minimal — it produces structural tokens only
- Semantic interpretation belongs in `AddressComponentVisitor`, not the grammar
- Adding a new street type or abbreviation is a lookup table update in the visitor

## Testing

- Add tests for new functionality
- Run the full suite before submitting: `./gradlew build`
- If you have ODA data available, validate with: `./gradlew cleanTest test -PodaBulk -PodaBulkOnly`

## Reporting Issues

- Use [GitHub Issues](../../issues) to report bugs or request features
- Include a sample address and expected output when reporting parsing issues

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
