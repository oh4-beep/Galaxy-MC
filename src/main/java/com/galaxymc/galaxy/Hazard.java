package com.galaxymc.galaxy;

/**
 * Natural disasters a world can throw at visitors. A planet's hazards are rolled with the rest of its
 * profile (see {@link FrontierPlanets}); {@link com.galaxymc.hazard.HazardManager} runs them.
 */
public enum Hazard {
    TORNADOES("Tornadoes", "Tornadoes tear across its open ground."),
    TSUNAMIS("Tsunamis", "Tsunamis rise out of its seas without warning."),
    ERUPTIONS("Eruptions", "Its volcanoes erupt, raining ash and molten rock."),
    METEORS("Meteor showers", "Meteors streak down from its sky and leave ore-rich craters."),
    LIGHTNING("Lightning storms", "Lightning storms scour its surface.");

    public final String displayName;
    public final String description;

    Hazard(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
