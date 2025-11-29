package src.pas.pokemon.senses;

// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Move.Category;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.linalg.Matrix;

public class CustomSensorArray extends SensorArray {
    // TODO: make fields if you want!

    public CustomSensorArray() {
        // TODO: intialize those fields if you make any!
    }

    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        // TODO: Convert a BattleView and a MoveView into a row-vector containing
        // measurements for every sense
        // you want your neural network to have. This method should be called if your
        // model is a q-based model
        List<Double> sensors = new ArrayList<>();

        // For now, assume Team1 is ours - agent should handle perspective
        TeamView myTeam = state.getTeam1View();
        TeamView oppTeam = state.getTeam2View();

        // Get the pokemon
        PokemonView myPokemon = myTeam.getActivePokemonView();
        PokemonView oppPokemon = oppTeam.getActivePokemonView();

        // Pokemon features
        sensors.addAll(exPokemonFeatures(myPokemon));
        sensors.addAll(exPokemonFeatures(oppPokemon));
        // Team features
        sensors.addAll(exTeamFeatures(myTeam));
        sensors.addAll(exTeamFeatures(oppTeam));
        // Matching features
        sensors.addAll(exMatchFeatures(myPokemon, oppPokemon));
        // Move features
        sensors.addAll(exMoveFeatures(action, myPokemon, oppPokemon));

        // Battle context features
        if (state.isOver()) {
            sensors.add(1.0);
        } else {
            sensors.add(0.0);
        }

        if (myPokemon != null && !myPokemon.hasFainted()) {
            sensors.add(1.0);
        } else {
            sensors.add(0.0);
        }

        if (oppPokemon != null && !oppPokemon.hasFainted()) {
            sensors.add(1.0);
        } else {
            sensors.add(0.0);
        }

        // 73 features total (20+20+4+4+6+16+3)
        // Enforce exactly 73 features: pad with zeros or truncate if needed
        final int EXPECTED_FEATURES = 73;
        while (sensors.size() < EXPECTED_FEATURES) {
            sensors.add(0.0);
        }
        if (sensors.size() > EXPECTED_FEATURES) {
            sensors = sensors.subList(0, EXPECTED_FEATURES);
        }

        // Convert List<Double> to Matrix row vector (1 x n)
        Matrix result = Matrix.zeros(1, sensors.size());
        for (int i = 0; i < sensors.size(); i++) {
            result.set(0, i, sensors.get(i));
        }
        return result;
    }

    // Extracting features form a single Pokemon
    private List<Double> exPokemonFeatures(PokemonView pkmn) {
        List<Double> features = new ArrayList<>();

        if (pkmn == null) {
            for (int i = 0; i < 20; i++) {
                features.add(0.0);
            }
            return features;
        }

        double maxHp = pkmn.getInitialStat(Stat.HP);
        double curHp = pkmn.getCurrentStat(Stat.HP);
        features.add(maxHp > 0 ? curHp / maxHp : 0.0); // ratio calculation

        double maxAtk = pkmn.getInitialStat(Stat.ATK);
        double maxDef = pkmn.getInitialStat(Stat.DEF);
        double maxSpatk = pkmn.getInitialStat(Stat.SPATK);
        double maxSpdef = pkmn.getInitialStat(Stat.SPDEF);
        double maxSpd = pkmn.getInitialStat(Stat.SPD);

        features.add(maxAtk > 0 ? pkmn.getCurrentStat(Stat.ATK) / maxAtk : 0.0);
        features.add(maxDef > 0 ? pkmn.getCurrentStat(Stat.DEF) / maxDef : 0.0);
        features.add(maxSpatk > 0 ? pkmn.getCurrentStat(Stat.SPATK) / maxSpatk : 0.0);
        features.add(maxSpdef > 0 ? pkmn.getCurrentStat(Stat.SPDEF) / maxSpdef : 0.0);
        features.add(maxSpd > 0 ? pkmn.getCurrentStat(Stat.SPD) / maxSpd : 0.0);

        // Normalizing with maximum pokemon level.
        features.add(pkmn.getLevel() / 100.0);

        NonVolatileStatus status = pkmn.getNonVolatileStatus();
        features.add(status == NonVolatileStatus.BURN ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.PARALYSIS ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.TOXIC ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.POISON ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.SLEEP ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.FREEZE ? 1.0 : 0.0);
        // stats will be normalized in the range of [0, 1]. Stat stages range from -6 to
        // +6.
        features.add((pkmn.getStatMultiplier(Stat.ATK) + 6) / 12.0);
        features.add((pkmn.getStatMultiplier(Stat.DEF) + 6) / 12.0);
        features.add((pkmn.getStatMultiplier(Stat.SPATK) + 6) / 12.0);
        features.add((pkmn.getStatMultiplier(Stat.SPDEF) + 6) / 12.0);
        features.add((pkmn.getStatMultiplier(Stat.SPD) + 6) / 12.0);
        features.add((pkmn.getStatMultiplier(Stat.ACC) + 6) / 12.0);
        features.add((pkmn.getStatMultiplier(Stat.EVASIVE) + 6) / 12.0);

        return features;
    }

    private List<Double> exTeamFeatures(TeamView team) {
        List<Double> features = new ArrayList<>();

        int count = 0;
        double totHP = 0.0;
        double totMaxHp = 0.0;

        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                count++;
                totHP += pkmn.getCurrentStat(Stat.HP);
                totMaxHp += pkmn.getInitialStat(Stat.HP);
            }
        }

        // Alive pokemon ratio
        features.add((double) count / Math.max(1, team.size()));
        // Average HP percentage
        features.add(totMaxHp > 0 ? totHP / totMaxHp : 0.0);
        // Alive reserved.
        features.add(count > 1 ? 1.0 : 0.0);
        // Team size normalized
        features.add(count / 6.0);
        return features; // 4 features in total.
    }

    // Checking the matching features between two pokemons.
    private List<Double> exMatchFeatures(PokemonView myPkmn, PokemonView oppPkmn) {
        List<Double> features = new ArrayList<>();

        if (myPkmn == null || myPkmn.hasFainted() || oppPkmn == null || oppPkmn.hasFainted()) {
            // Fill with zeros if any pokemon is null or fainted.
            for (int i = 0; i < 6; i++) {
                features.add(0.0);
            }
            return features;
        }

        double mySpeed = myPkmn.getCurrentStat(Stat.SPD);
        double oppSpeed = oppPkmn.getCurrentStat(Stat.SPD);
        features.add(mySpeed > oppSpeed ? 1.0 : 0.0);

        Type myType1 = myPkmn.getCurrentType1();
        Type myType2 = myPkmn.getCurrentType2();
        Type oppType1 = oppPkmn.getCurrentType1();
        Type oppType2 = oppPkmn.getCurrentType2();

        double myAttAdvantage = 0.0;
        double oppAttAdvantage = 0.0;

        if (myType1 != null && oppType1 != null) {
            myAttAdvantage = Math.max(myAttAdvantage, Type.getEffectivenessModifier(myType1, oppType1));
        }
        if (myType1 != null && oppType2 != null) {
            myAttAdvantage = Math.max(myAttAdvantage, Type.getEffectivenessModifier(myType1, oppType2));
        }
        if (myType2 != null && oppType1 != null) {
            myAttAdvantage = Math.max(myAttAdvantage, Type.getEffectivenessModifier(myType2, oppType1));
        }
        if (myType2 != null && oppType2 != null) {
            myAttAdvantage = Math.max(myAttAdvantage, Type.getEffectivenessModifier(myType2, oppType2));
        }
        if (oppType1 != null && myType1 != null) {
            oppAttAdvantage = Math.max(oppAttAdvantage, Type.getEffectivenessModifier(oppType1, myType1));
        }
        if (oppType1 != null && myType2 != null) {
            oppAttAdvantage = Math.max(oppAttAdvantage, Type.getEffectivenessModifier(oppType1, myType2));
        }
        if (oppType2 != null && myType1 != null) {
            oppAttAdvantage = Math.max(oppAttAdvantage, Type.getEffectivenessModifier(oppType2, myType1));
        }
        if (oppType2 != null && myType2 != null) {
            oppAttAdvantage = Math.max(oppAttAdvantage, Type.getEffectivenessModifier(oppType2, myType2));
        }

        // Normalizing the advantage: effectiveness ranges from 0 to 4.
        features.add(myAttAdvantage / 4.0);
        features.add(oppAttAdvantage / 4.0);

        double myInitHP = myPkmn.getInitialStat(Stat.HP);
        double oppInitHP = oppPkmn.getInitialStat(Stat.HP);
        double myHpRatio = myInitHP > 0 ? myPkmn.getCurrentStat(Stat.HP) / (double) myInitHP : 0.0;
        double oppHpRatio = oppInitHP > 0 ? oppPkmn.getCurrentStat(Stat.HP) / (double) oppInitHP : 0.0;
        features.add(myHpRatio);
        features.add(oppHpRatio);

        int myBoost = myPkmn.getStatMultiplier(Stat.ATK) + myPkmn.getStatMultiplier(Stat.SPATK)
                + myPkmn.getStatMultiplier(Stat.SPD);
        int oppBoost = oppPkmn.getStatMultiplier(Stat.ATK) + oppPkmn.getStatMultiplier(Stat.SPATK)
                + oppPkmn.getStatMultiplier(Stat.SPD);
        features.add(myBoost > oppBoost ? 1.0 : 0.0);
        return features;
    }

    private List<Double> exMoveFeatures(MoveView move, PokemonView attacker, PokemonView defender) {
        List<Double> features = new ArrayList<>();

        if (move == null) {
            // return zeros if there is no move
            for (int i = 0; i < 16; i++) {
                features.add(0.0);
            }
            return features;
        }

        boolean isSwitch = move.getName() != null
                && (move.getName().toLowerCase().contains("switch") || move.getName().equals("SwitchMove"));
        features.add(isSwitch ? 1.0 : 0.0);

        Integer pow = move.getPower();
        features.add(pow != null ? Math.min(pow / 250.0, 1.0) : 0.0); // Max realistic power is 250

        Integer acc = move.getAccuracy();
        features.add(acc != null ? acc / 100.0 : 0.0); // Normalized accuracy

        features.add((move.getPriority() + 7) / 12.0);

        Category cat = move.getCategory();
        features.add(cat == Category.PHYSICAL ? 1.0 : 0.0);
        features.add(cat == Category.SPECIAL ? 1.0 : 0.0);
        features.add(cat == Category.STATUS ? 1.0 : 0.0);

        Type moveType = move.getType();
        boolean stabing = false;
        if (attacker != null && moveType != null) {
            Type atkType1 = attacker.getCurrentType1();
            Type atkType2 = attacker.getCurrentType2();
            stabing = (moveType == atkType1) || (moveType == atkType2);
        }
        features.add(stabing ? 1.0 : 0.0);
        features.add(moveType != null ? moveType.ordinal() / 14.0 : 0.0); // There are 15 types (0-14).

        double effect1 = 1.0;
        double effect2 = 1.0;
        if (defender != null && moveType != null) {
            Type defType1 = defender.getCurrentType1();
            Type defType2 = defender.getCurrentType2();

            if (defType1 != null) {
                effect1 = Type.getEffectivenessModifier(moveType, defType1);
            }
            if (defType2 != null) {
                effect2 = Type.getEffectivenessModifier(moveType, defType2);
            }
        }

        double totalEffect = effect1 * effect2;
        features.add(totalEffect / 4.0); // Total effectiveness ranges [0, 4] to [0, 1]
        features.add(totalEffect > 1.0 ? 1.0 : 0.0); // super effective
        features.add(totalEffect < 1.0 ? 1.0 : 0.0); // not very effective
        features.add(totalEffect == 0.0 ? 1.0 : 0.0); // immune

        // Pivot movement detection
        boolean isPivot = move.getName() != null &&
                (move.getName().toLowerCase().contains("u-turn") ||
                        move.getName().toLowerCase().contains("volt switch") ||
                        move.getName().toLowerCase().contains("flip turn"));
        features.add(isPivot ? 1.0 : 0.0);

        // Boosting the stats detection
        boolean isSetup = move.getName() != null &&
                (move.getName().toLowerCase().contains("dance") ||
                        move.getName().toLowerCase().contains("swords") ||
                        move.getName().toLowerCase().contains("calm mind") ||
                        move.getName().toLowerCase().contains("quiver"));
        features.add(isSetup ? 1.0 : 0.0);

        // Check if it's weak move or not.
        features.add((pow != null && pow < 40 && pow > 0) ? 1.0 : 0.0);

        return features;
    }
}