package src.pas.pokemon.senses;

// SYSTEM IMPORTS

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
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.linalg.Matrix;

public class CustomSensorArray extends SensorArray {

    // Fixed feature count - 65 features. Including the bias term. 
    private static final int NUM_FEATURES = 65;

    public CustomSensorArray() {
        super();
    }

    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        // Simplified to essential features only since too many feature cause the noise
        // Also causing it hard time to converge during training.
        Matrix features = Matrix.zeros(1, NUM_FEATURES);
        int index = 0;

        // Think team 1 is always my team team 2 is always opponent. 
        TeamView myTeam = state.getTeam1View();
        TeamView oppTeam = state.getTeam2View();
        PokemonView myPokemon = myTeam.getActivePokemonView();
        PokemonView oppPokemon = oppTeam.getActivePokemonView();

        // HP Features to track down. 
        if (myPokemon != null) {
            int currentHP = myPokemon.getCurrentStat(Stat.HP);
            int maxHP = myPokemon.getInitialStat(Stat.HP);
            features.set(0, index++, maxHP > 0 ? (double) currentHP / maxHP : 0.0);
        } else {
            features.set(0, index++, 0.0);
        }
        if (oppPokemon != null) {
            int currentHP = oppPokemon.getCurrentStat(Stat.HP);
            int maxHP = oppPokemon.getInitialStat(Stat.HP);
            features.set(0, index++, maxHP > 0 ? (double) currentHP / maxHP : 0.0);
        } else {
            features.set(0, index++, 0.0);
        }

        // alive count for both teams. 
        int myAliveCount = 0;
        for (int i = 0; i < myTeam.size(); i++) {
            if (!myTeam.getPokemonView(i).hasFainted()) myAliveCount++;
        }
        features.set(0, index++, (double) myAliveCount / Math.max(1, myTeam.size()));
        
        int oppAliveCount = 0;
        for (int i = 0; i < oppTeam.size(); i++) {
            if (!oppTeam.getPokemonView(i).hasFainted()) oppAliveCount++;
        }
        features.set(0, index++, (double) oppAliveCount / Math.max(1, oppTeam.size()));

        // status of the pokemon. One HOT encoding for the main status conditions. 
        index = setStatusOneHot(features, index, myPokemon);
        index = setStatusOneHot(features, index, oppPokemon);

        // stat multipliers for both of the pokemon. 
        index = setStatMultipliers(features, index, myPokemon);
        index = setStatMultipliers(features, index, oppPokemon);

        // track down the volatile status.
        index = setVolatileStatus(features, index, myPokemon);
        index = setVolatileStatus(features, index, oppPokemon);

        // team's total HP count. 
        double myTotHP = 0.0, myTotMaxHP = 0.0;
        for (int i = 0; i < myTeam.size(); i++) {
            PokemonView p = myTeam.getPokemonView(i);
            if (p != null) {
                myTotHP += p.getCurrentStat(Stat.HP);
                myTotMaxHP += p.getInitialStat(Stat.HP);
            }
        }
        features.set(0, index++, myTotMaxHP > 0 ? myTotHP / myTotMaxHP : 0.0);
        
        double oppTotHP = 0.0, oppTotMaxHP = 0.0;
        for (int i = 0; i < oppTeam.size(); i++) {
            PokemonView p = oppTeam.getPokemonView(i);
            if (p != null) {
                oppTotHP += p.getCurrentStat(Stat.HP);
                oppTotMaxHP += p.getInitialStat(Stat.HP);
            }
        }
        features.set(0, index++, oppTotMaxHP > 0 ? oppTotHP / oppTotMaxHP : 0.0);

        // move features. 
        index = setMoveFeatures(features, index, action, myPokemon, oppPokemon);

        // unable to perform move status.
        if (action != null) {
            features.set(0, index++, action.getNumDisabledTurnsRemaining() > 0 ? 1.0 : 0.0);
        } else {
            features.set(0, index++, 0.0);
        }

        // battle context features. 
        features.set(0, index++, state.isOver() ? 1.0 : 0.0);
        features.set(0, index++, (myPokemon != null && !myPokemon.hasFainted()) ? 1.0 : 0.0);
        features.set(0, index++, (oppPokemon != null && !oppPokemon.hasFainted()) ? 1.0 : 0.0);

        // bias term. 
        features.set(0, index++, 1.0);

        /* extra features maybe enrolled to use later. 
        index = setPokemonFeatures(features, index, myPokemon);

        // opponent pokemon features 
        index = setPokemonFeatures(features, index, oppPokemon);

        // one-hot encoding. 
        index = setTypeOneHot(features, index, myPokemon, true); 
        index = setTypeOneHot(features, index, myPokemon, false); 
        index = setTypeOneHot(features, index, oppPokemon, true); 
        index = setTypeOneHot(features, index, oppPokemon, false); 

        // volatile status. 
        index = setVolatileStatus(features, index, myPokemon); 
        index = setVolatileStatus(features, index, oppPokemon); 

        // counters for status conditions.
        index = setStatusCounters(features, index, myPokemon); 
        index = setStatusCounters(features, index, oppPokemon); 

        // team features. 
        index = setTeamFeatures(features, index, myTeam); 
        index = setTeamFeatures(features, index, oppTeam); 

        // count of the fainted pokemon. 
        index = setFaintedCount(features, index, myTeam); 
        index = setFaintedCount(features, index, oppTeam); 
        index = setMatchFeatures(features, index, myPokemon, oppPokemon);
        index = setMoveFeatures(features, index, action, myPokemon, oppPokemon);
        index = setMoveTypeEffectiveness(features, index, myPokemon, oppPokemon);
        features.set(0, index++, state.isOver() ? 1.0 : 0.0);
        features.set(0, index++, (myPokemon != null && !myPokemon.hasFainted()) ? 1.0 : 0.0);
        features.set(0, index++, (oppPokemon != null && !oppPokemon.hasFainted()) ? 1.0 : 0.0);
        */


        return features;
    }

    // Set 5 volatile status features
    private int setVolatileStatus(Matrix features, int startIdx, PokemonView pkmn) {
        if (pkmn == null) {
            for (int i = 0; i < 5; i++) {
                features.set(0, startIdx + i, 0.0);
            }
        } else {
            features.set(0, startIdx, pkmn.getFlag(Flag.CONFUSED) ? 1.0 : 0.0);
            features.set(0, startIdx + 1, pkmn.getFlag(Flag.FLINCHED) ? 1.0 : 0.0);
            features.set(0, startIdx + 2, pkmn.getFlag(Flag.SEEDED) ? 1.0 : 0.0);
            features.set(0, startIdx + 3, pkmn.getFlag(Flag.TRAPPED) ? 1.0 : 0.0);
            features.set(0, startIdx + 4, pkmn.getFlag(Flag.FOCUS_ENERGY) ? 1.0 : 0.0);
        }
        return startIdx + 5;
    }

    /* status counter
    // Set 2 status counter features
    private int setStatusCounters(Matrix features, int startIdx, PokemonView pkmn) {
        if (pkmn == null) {
            features.set(0, startIdx, 0.0);
            features.set(0, startIdx + 1, 0.0);
        } else {
            int sleepCounter = pkmn.getNonVolatileStatusCounter(NonVolatileStatus.SLEEP);
            features.set(0, startIdx, sleepCounter / 7.0);

            int toxicCounter = pkmn.getNonVolatileStatusCounter(NonVolatileStatus.TOXIC);
            features.set(0, startIdx + 1, Math.min(toxicCounter / 15.0, 1.0));
        }
        return startIdx + 2;
    }
    */

    /* team features in more detail. 
    // Set 4 team features
    private int setTeamFeatures(Matrix features, int startIdx, TeamView team) {
        int count = 0;
        double totHP = 0.0;
        double totMaxHP = 0.0;

        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                count++;
                totHP += pkmn.getCurrentStat(Stat.HP);
                totMaxHP += pkmn.getInitialStat(Stat.HP);
            }
        }

        features.set(0, startIdx, (double) count / Math.max(1, team.size()));
        features.set(0, startIdx + 1, totMaxHP > 0 ? totHP / totMaxHP : 0.0);
        features.set(0, startIdx + 2, count > 1 ? 1.0 : 0.0);
        features.set(0, startIdx + 3, count / 6.0);

        return startIdx + 4;
    }
    */

    /* number of fainted pokemon. 
    // Set 1 fainted count feature
    private int setFaintedCount(Matrix features, int startIdx, TeamView team) {
        int faintedCount = 0;
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && pkmn.hasFainted()) {
                faintedCount++;
            }
        }
        features.set(0, startIdx, faintedCount / 6.0);
        return startIdx + 1;
    }
    */

    /* match the features in more details. 
    // Set 6 match features
    private int setMatchFeatures(Matrix features, int startIdx, PokemonView myPkmn, PokemonView oppPkmn) {
        if (myPkmn == null || myPkmn.hasFainted() || oppPkmn == null || oppPkmn.hasFainted()) {
            for (int i = 0; i < 6; i++) {
                features.set(0, startIdx + i, 0.0);
            }
            return startIdx + 6;
        }

        int index = startIdx;

        // Speed advantage
        double mySpeed = myPkmn.getCurrentStat(Stat.SPD);
        double oppSpeed = oppPkmn.getCurrentStat(Stat.SPD);
        features.set(0, index++, mySpeed > oppSpeed ? 1.0 : 0.0);

        // Type advantages
        Type myType1 = myPkmn.getCurrentType1();
        Type myType2 = myPkmn.getCurrentType2();
        Type oppType1 = oppPkmn.getCurrentType1();
        Type oppType2 = oppPkmn.getCurrentType2();

        double myAttAdv = 0.0;
        double oppAttAdv = 0.0;

        if (myType1 != null && oppType1 != null)
            myAttAdv = Math.max(myAttAdv, Type.getEffectivenessModifier(myType1, oppType1));
        if (myType1 != null && oppType2 != null)
            myAttAdv = Math.max(myAttAdv, Type.getEffectivenessModifier(myType1, oppType2));
        if (myType2 != null && oppType1 != null)
            myAttAdv = Math.max(myAttAdv, Type.getEffectivenessModifier(myType2, oppType1));
        if (myType2 != null && oppType2 != null)
            myAttAdv = Math.max(myAttAdv, Type.getEffectivenessModifier(myType2, oppType2));
        if (oppType1 != null && myType1 != null)
            oppAttAdv = Math.max(oppAttAdv, Type.getEffectivenessModifier(oppType1, myType1));
        if (oppType1 != null && myType2 != null)
            oppAttAdv = Math.max(oppAttAdv, Type.getEffectivenessModifier(oppType1, myType2));
        if (oppType2 != null && myType1 != null)
            oppAttAdv = Math.max(oppAttAdv, Type.getEffectivenessModifier(oppType2, myType1));
        if (oppType2 != null && myType2 != null)
            oppAttAdv = Math.max(oppAttAdv, Type.getEffectivenessModifier(oppType2, myType2));

        features.set(0, index++, myAttAdv / 4.0);
        features.set(0, index++, oppAttAdv / 4.0);

        // HP ratios
        double myInitHP = myPkmn.getInitialStat(Stat.HP);
        double oppInitHP = oppPkmn.getInitialStat(Stat.HP);
        features.set(0, index++, myInitHP > 0 ? myPkmn.getCurrentStat(Stat.HP) / (double) myInitHP : 0.0);
        features.set(0, index++, oppInitHP > 0 ? oppPkmn.getCurrentStat(Stat.HP) / (double) oppInitHP : 0.0);

        // Boost comparison
        int myBoost = myPkmn.getStatMultiplier(Stat.ATK) + myPkmn.getStatMultiplier(Stat.SPATK)
                + myPkmn.getStatMultiplier(Stat.SPD);
        int oppBoost = oppPkmn.getStatMultiplier(Stat.ATK) + oppPkmn.getStatMultiplier(Stat.SPATK)
                + oppPkmn.getStatMultiplier(Stat.SPD);
        features.set(0, index++, myBoost > oppBoost ? 1.0 : 0.0);

        return index;
    }
    */

    // Set the 16 move features. 
    private int setMoveFeatures(Matrix features, int startIdx, MoveView move, PokemonView attacker,
            PokemonView defender) {
        if (move == null) {
            for (int i = 0; i < 16; i++) {
                features.set(0, startIdx + i, 0.0);
            }
            return startIdx + 16;
        }

        int index = startIdx;

        // Move flags
        boolean isSwitch = move.getName() != null
                && (move.getName().toLowerCase().contains("switch") || move.getName().equals("SwitchMove"));
        features.set(0, index++, isSwitch ? 1.0 : 0.0);

        // Power and accuracy
        Integer pow = move.getPower();
        features.set(0, index++, pow != null ? Math.min(pow / 250.0, 1.0) : 0.0);

        Integer acc = move.getAccuracy();
        features.set(0, index++, acc != null ? acc / 100.0 : 0.0);

        // Priority
        features.set(0, index++, (move.getPriority() + 6) / 11.0);

        // Category
        Category cat = move.getCategory();
        features.set(0, index++, cat == Category.PHYSICAL ? 1.0 : 0.0);
        features.set(0, index++, cat == Category.SPECIAL ? 1.0 : 0.0);
        features.set(0, index++, cat == Category.STATUS ? 1.0 : 0.0);

        // STAB effect
        Type moveType = move.getType();
        boolean stab = false;
        if (attacker != null && moveType != null) {
            Type atkType1 = attacker.getCurrentType1();
            Type atkType2 = attacker.getCurrentType2();
            stab = (moveType == atkType1) || (moveType == atkType2);
        }
        features.set(0, index++, stab ? 1.0 : 0.0);
        features.set(0, index++, moveType != null ? moveType.ordinal() / 15.0 : 0.0);

        // Type effectiveness
        double effect1 = 1.0;
        double effect2 = 1.0;
        if (defender != null && moveType != null) {
            Type defType1 = defender.getCurrentType1();
            Type defType2 = defender.getCurrentType2();
            if (defType1 != null)
                effect1 = Type.getEffectivenessModifier(moveType, defType1);
            if (defType2 != null)
                effect2 = Type.getEffectivenessModifier(moveType, defType2);
        }

        double totalEffect = effect1 * effect2;
        features.set(0, index++, totalEffect / 4.0);
        features.set(0, index++, totalEffect > 1.0 ? 1.0 : 0.0);
        features.set(0, index++, totalEffect < 1.0 ? 1.0 : 0.0);
        features.set(0, index++, totalEffect == 0.0 ? 1.0 : 0.0);

        // Special move types
        boolean isPivot = move.getName() != null &&
                (move.getName().toLowerCase().contains("u-turn") ||
                        move.getName().toLowerCase().contains("volt switch") ||
                        move.getName().toLowerCase().contains("flip turn"));
        features.set(0, index++, isPivot ? 1.0 : 0.0);

        boolean isSetup = move.getName() != null &&
                (move.getName().toLowerCase().contains("dance") ||
                        move.getName().toLowerCase().contains("swords") ||
                        move.getName().toLowerCase().contains("calm mind") ||
                        move.getName().toLowerCase().contains("quiver"));
        features.set(0, index++, isSetup ? 1.0 : 0.0);

        features.set(0, index++, (pow != null && pow < 40 && pow > 0) ? 1.0 : 0.0);

        return index; 
    }

    // Status encdoing on-hot
    private int setStatusOneHot(Matrix features, int startIdx, PokemonView pkmn) {
        int index = startIdx;
        NonVolatileStatus currentStatus = (pkmn != null) ? pkmn.getNonVolatileStatus() : null;
        
        // nonvolatile status one-hot encoding
        for (NonVolatileStatus status : NonVolatileStatus.values()) {
            features.set(0, index++, (currentStatus == status) ? 1.0 : 0.0);
        }
        
        return index; 
    }

    // stage multipliers for the 7 stats. 
    private int setStatMultipliers(Matrix features, int startIdx, PokemonView pkmn) {
        if (pkmn == null) {
            for (int i = 0; i < 7; i++) {
                features.set(0, startIdx + i, 0.0);
            }
        } else {
            features.set(0, startIdx, (pkmn.getStatMultiplier(Stat.ATK) + 6) / 12.0);
            features.set(0, startIdx + 1, (pkmn.getStatMultiplier(Stat.DEF) + 6) / 12.0);
            features.set(0, startIdx + 2, (pkmn.getStatMultiplier(Stat.SPATK) + 6) / 12.0);
            features.set(0, startIdx + 3, (pkmn.getStatMultiplier(Stat.SPDEF) + 6) / 12.0);
            features.set(0, startIdx + 4, (pkmn.getStatMultiplier(Stat.SPD) + 6) / 12.0);
            features.set(0, startIdx + 5, (pkmn.getStatMultiplier(Stat.ACC) + 6) / 12.0);
            features.set(0, startIdx + 6, (pkmn.getStatMultiplier(Stat.EVASIVE) + 6) / 12.0);
        }
        return startIdx + 7;
    }
}