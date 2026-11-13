package com.github.vvi3ard.dt.linkifier.model;

/**
 * Mode of link activation for a particular scope.
 */
public enum ActivationMode {

    DISABLED("—", "Отключено"),
    ALWAYS("Всегда", "Всегда активно (без удержания Ctrl)"),
    CTRL_CLICK("Ctrl + Click", "Активно при удержании Ctrl");

    private final String label;
    private final String description;

    ActivationMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Cycles to the next mode: DISABLED -> ALWAYS -> CTRL_CLICK -> DISABLED.
     */
    public ActivationMode next() {
        switch (this) {
            case DISABLED:
                return ALWAYS;
            case ALWAYS:
                return CTRL_CLICK;
            case CTRL_CLICK:
            default:
                return DISABLED;
        }
    }

    public static ActivationMode fromString(String str) {
        if (str == null || str.trim().isEmpty() || str.equals("—")) {
            return DISABLED;
        }
        for (ActivationMode m : values()) {
            if (m.name().equalsIgnoreCase(str) || m.label.equalsIgnoreCase(str)) {
                return m;
            }
        }
        return DISABLED;
    }
}
