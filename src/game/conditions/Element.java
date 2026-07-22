package game.conditions;

public enum Element {
    WATER("Water"),
    FIRE("Fire"),
    GRASS("Grass");

    private final String label;

    Element(String label){
        this.label = label;
    }

    /**
     *
     * @return the label text
     */
    @Override
    public String toString() {
        return label;
    }
}
