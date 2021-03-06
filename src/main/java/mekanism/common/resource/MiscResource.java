package mekanism.common.resource;

public enum MiscResource implements INamedResource {
    BRONZE("bronze"),
    CARBON("carbon"),
    CHARCOAL("charcoal"),
    COAL("coal"),
    DIAMOND("diamond"),
    EMERALD("emerald"),
    LAPIS_LAZULI("lapis_lazuli"),
    LITHIUM("lithium"),
    OBSIDIAN("obsidian"),
    QUARTZ("quartz"),
    REDSTONE("redstone"),
    REFINED_GLOWSTONE("refined_glowstone"),
    REFINED_OBSIDIAN("refined_obsidian"),
    STEEL("steel"),
    SULFUR("sulfur");

    private final String registrySuffix;

    MiscResource(String registrySuffix) {
        this.registrySuffix = registrySuffix;
    }

    @Override
    public String getRegistrySuffix() {
        return registrySuffix;
    }
}