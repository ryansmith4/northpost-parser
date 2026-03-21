package com.guidedbyte.address.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the parsed components of a Canadian postal address. This class uses Java 21 features including records for
 * immutability and pattern matching capabilities.
 */
public record AddressComponents(
        String addressee,
        String careOf,
        String unitNumber,
        String streetNumber,
        String streetName,
        String streetType,
        String streetDirection,
        String postalBoxNumber,
        String ruralRoute,
        String siteInfo,
        String municipality,
        String province,
        String postalCode,
        String country,
        AddressType addressType,
        ParsingMode parsingMode,
        boolean isComplete) {

    /** Enumeration of Canadian address types */
    public enum AddressType {
        CIVIC,
        POSTAL_BOX,
        RURAL_ROUTE,
        GENERAL_DELIVERY,
        MIXED,
        INCOMPLETE
    }

    /** Enumeration of parsing modes used */
    public enum ParsingMode {
        STRICT,
        LENIENT
    }

    /** Compact constructor with validation */
    public AddressComponents {
        // Normalize null values to empty strings for consistency
        addressee = normalize(addressee);
        careOf = normalize(careOf);
        unitNumber = normalize(unitNumber);
        streetNumber = normalize(streetNumber);
        streetName = normalize(streetName);
        streetType = normalize(streetType);
        streetDirection = normalize(streetDirection);
        postalBoxNumber = normalize(postalBoxNumber);
        ruralRoute = normalize(ruralRoute);
        siteInfo = normalize(siteInfo);
        municipality = normalize(municipality);
        province = normalize(province);
        postalCode = normalize(postalCode);
        country = normalize(country);

        // Validate required parameters
        Objects.requireNonNull(addressType, "Address type cannot be null");
        Objects.requireNonNull(parsingMode, "Parsing mode cannot be null");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    /** Builder pattern for creating AddressComponents */
    public static class Builder {
        private String addressee = "";
        private String careOf = "";
        private String unitNumber = "";
        private String streetNumber = "";
        private String streetName = "";
        private String streetType = "";
        private String streetDirection = "";
        private String postalBoxNumber = "";
        private String ruralRoute = "";
        private String siteInfo = "";
        private String municipality = "";
        private String province = "";
        private String postalCode = "";
        private String country = "";
        private AddressType addressType = AddressType.INCOMPLETE;
        private ParsingMode parsingMode = ParsingMode.LENIENT;
        private boolean isComplete = false;

        public Builder addressee(String addressee) {
            this.addressee = addressee;
            return this;
        }

        public Builder careOf(String careOf) {
            this.careOf = careOf;
            return this;
        }

        public Builder unitNumber(String unitNumber) {
            this.unitNumber = unitNumber;
            return this;
        }

        public Builder streetNumber(String streetNumber) {
            this.streetNumber = streetNumber;
            return this;
        }

        public Builder streetName(String streetName) {
            this.streetName = streetName;
            return this;
        }

        public Builder streetType(String streetType) {
            this.streetType = streetType;
            return this;
        }

        public Builder streetDirection(String streetDirection) {
            this.streetDirection = streetDirection;
            return this;
        }

        public Builder postalBoxNumber(String postalBoxNumber) {
            this.postalBoxNumber = postalBoxNumber;
            return this;
        }

        public Builder ruralRoute(String ruralRoute) {
            this.ruralRoute = ruralRoute;
            return this;
        }

        public Builder siteInfo(String siteInfo) {
            this.siteInfo = siteInfo;
            return this;
        }

        public Builder municipality(String municipality) {
            this.municipality = municipality;
            return this;
        }

        public Builder province(String province) {
            this.province = province;
            return this;
        }

        public Builder postalCode(String postalCode) {
            this.postalCode = postalCode;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder addressType(AddressType addressType) {
            this.addressType = addressType;
            return this;
        }

        public Builder parsingMode(ParsingMode parsingMode) {
            this.parsingMode = parsingMode;
            return this;
        }

        public Builder isComplete(boolean isComplete) {
            this.isComplete = isComplete;
            return this;
        }

        public AddressComponents build() {
            return new AddressComponents(
                    addressee,
                    careOf,
                    unitNumber,
                    streetNumber,
                    streetName,
                    streetType,
                    streetDirection,
                    postalBoxNumber,
                    ruralRoute,
                    siteInfo,
                    municipality,
                    province,
                    postalCode,
                    country,
                    addressType,
                    parsingMode,
                    isComplete);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience methods for checking component availability */
    public boolean hasAddressee() {
        return !addressee.isEmpty();
    }

    public boolean hasCareOf() {
        return !careOf.isEmpty();
    }

    public boolean hasUnitNumber() {
        return !unitNumber.isEmpty();
    }

    public boolean hasStreetNumber() {
        return !streetNumber.isEmpty();
    }

    public boolean hasStreetName() {
        return !streetName.isEmpty();
    }

    public boolean hasStreetType() {
        return !streetType.isEmpty();
    }

    public boolean hasStreetDirection() {
        return !streetDirection.isEmpty();
    }

    public boolean hasPostalBoxNumber() {
        return !postalBoxNumber.isEmpty();
    }

    public boolean hasRuralRoute() {
        return !ruralRoute.isEmpty();
    }

    public boolean hasMunicipality() {
        return !municipality.isEmpty();
    }

    public boolean hasProvince() {
        return !province.isEmpty();
    }

    public boolean hasPostalCode() {
        return !postalCode.isEmpty();
    }

    public boolean hasCountry() {
        return !country.isEmpty();
    }

    /** Returns the full street address as a formatted string */
    public Optional<String> getFormattedStreetAddress() {
        StringBuilder sb = new StringBuilder();

        if (hasUnitNumber()) {
            sb.append(unitNumber).append("-");
        }

        if (hasStreetNumber()) {
            sb.append(streetNumber).append(" ");
        }

        if (hasStreetName()) {
            sb.append(streetName);
            if (hasStreetType()) {
                sb.append(" ").append(streetType);
            }
            if (hasStreetDirection()) {
                sb.append(" ").append(streetDirection);
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    /**
     * Returns the full street designation: name + type + direction combined. E.g., "EVERITT DRIVE NORTH", "MAIN
     * STREET", "RUE PRINCIPALE O". This is the complete street identifier as it would appear on signage.
     */
    public Optional<String> getFullStreetName() {
        if (!hasStreetName()) return Optional.empty();

        StringBuilder sb = new StringBuilder(streetName);
        if (hasStreetType()) sb.append(" ").append(streetType);
        if (hasStreetDirection()) sb.append(" ").append(streetDirection);

        return Optional.of(sb.toString());
    }

    /** Returns the full civic address line */
    public Optional<String> getFormattedCivicLine() {
        return switch (addressType) {
            case CIVIC -> getFormattedStreetAddress();
            case POSTAL_BOX -> hasPostalBoxNumber() ? Optional.of("PO BOX " + postalBoxNumber) : Optional.empty();
            case RURAL_ROUTE -> hasRuralRoute() ? Optional.of("RR " + ruralRoute) : Optional.empty();
            case GENERAL_DELIVERY -> Optional.of("GENERAL DELIVERY");
            default -> Optional.empty();
        };
    }

    /** Returns the formatted municipality/province/postal code line */
    public Optional<String> getFormattedLastLine() {
        StringBuilder sb = new StringBuilder();

        if (hasMunicipality()) {
            sb.append(municipality);
        }

        if (hasProvince()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(province);
        }

        if (hasPostalCode()) {
            if (!sb.isEmpty()) sb.append("  ");
            sb.append(postalCode);
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    /** Returns a formatted multi-line address string */
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();

        if (hasAddressee()) {
            sb.append(addressee).append("\n");
        }

        if (hasCareOf()) {
            sb.append("C/O ").append(careOf).append("\n");
        }

        getFormattedCivicLine().ifPresent(line -> sb.append(line).append("\n"));
        getFormattedLastLine().ifPresent(line -> sb.append(line).append("\n"));

        if (hasCountry()) {
            sb.append(country).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Returns a new AddressComponents with street type and direction normalized to canonical forms.
     *
     * <p>The parser preserves input forms by default. Use this method to convert to a consistent canonical form for
     * address matching, deduplication, or storage.
     *
     * <p>Example:
     *
     * <pre>
     *   var normalized = components.normalize(NormalizationStrategy.FULL_FORM);
     *   normalized.streetType();      // "BOULEVARD" (was "BLVD")
     *   normalized.streetDirection(); // "NORTH" (was "N")
     * </pre>
     *
     * @param strategy the normalization strategy to apply
     * @return a new AddressComponents with normalized street type and direction
     */
    public AddressComponents normalize(NormalizationStrategy strategy) {
        return new AddressComponents(
                addressee,
                careOf,
                unitNumber,
                streetNumber,
                streetName,
                AddressNormalizer.normalizeStreetType(streetType, strategy),
                AddressNormalizer.normalizeDirection(streetDirection, strategy),
                postalBoxNumber,
                ruralRoute,
                siteInfo,
                municipality,
                province,
                postalCode,
                country,
                addressType,
                parsingMode,
                isComplete);
    }

    /** Validates that the address has minimum required components for mailing */
    public boolean isMailingValid() {
        return hasAddressee()
                && (hasStreetName() || hasPostalBoxNumber() || hasRuralRoute())
                && hasMunicipality()
                && hasProvince()
                && hasPostalCode();
    }

    @Override
    public String toString() {
        return "AddressComponents{" + "addressType="
                + addressType + ", parsingMode="
                + parsingMode + ", isComplete="
                + isComplete + ", addressee='"
                + addressee + '\'' + (hasCareOf() ? ", careOf='" + careOf + '\'' : "")
                + (hasStreetNumber() ? ", streetNumber='" + streetNumber + '\'' : "")
                + (hasStreetName() ? ", streetName='" + streetName + '\'' : "")
                + (hasStreetType() ? ", streetType='" + streetType + '\'' : "")
                + (hasMunicipality() ? ", municipality='" + municipality + '\'' : "")
                + (hasProvince() ? ", province='" + province + '\'' : "")
                + (hasPostalCode() ? ", postalCode='" + postalCode + '\'' : "")
                + '}';
    }
}
