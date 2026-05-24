package io.mehdieidi.modless.platform.core.model;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.util.Locale;

public enum ModelLevel {
    CIM,
    PIM,
    PSM;

    public static ModelLevel fromApiName(String value) {
        try {
            return ModelLevel.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new PlatformException(400, "Unsupported model level: " + value);
        }
    }

    public String apiName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
