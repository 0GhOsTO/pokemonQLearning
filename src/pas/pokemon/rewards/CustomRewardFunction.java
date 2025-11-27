package src.pas.pokemon.rewards;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.Team.TeamView;

public class CustomRewardFunction
        extends RewardFunction {

    public CustomRewardFunction() {
        // Defining as R(s, a, s')
        super(RewardType.STATE_ACTION_STATE); // currently configured to produce rewards as a function of the state
    }

    public double getLowerBound() {
        // TODO: change this. Reward values must be finite!
        return -5000.0;
    }

    public double getUpperBound() {
        // TODO: change this. Reward values must be finite!
        return 5000.0;
    }

    // NOT USED
    public double getStateReward(final BattleView state) {
        return 0d;
    }

    // NOT USED
    public double getStateActionReward(final BattleView state,
            final MoveView action) {
        return 0d;
    }

    public double getStateActionStateReward(final BattleView state,
            final MoveView action,
            final BattleView nextState) {
        double reward = 0.0;

        // Treating team 2 as the oponent.
        TeamView myTeam = state.getTeam1View();
        TeamView oppTeam = state.getTeam2View();
        TeamView myNxtTeam = nextState.getTeam1View();
        TeamView oppNxtTeam = nextState.getTeam2View();

        // Get the active pokemon for the both team.
        PokemonView myPokemon = myTeam.getActivePokemonView();
        PokemonView oppPokemon = oppTeam.getActivePokemonView();
        PokemonView myNxtPokemon = myNxtTeam.getActivePokemonView();
        PokemonView oppNxtPokemon = oppNxtTeam.getActivePokemonView();

        // If the battle is over ...
        if (nextState.isOver()) {
            if (teamDead(oppNxtTeam)) {
                reward += 1500.0; // Win (reduced from 2500 to balance with other rewards)
            } else if (teamDead(myNxtTeam)) {
                reward -= 1500.0; // Lose
            }
            // Game is over
            return reward;
        }

        // Pokemon dead.
        if (oppPokemon != null && oppNxtPokemon != null) {
            if (!oppPokemon.hasFainted() && oppNxtPokemon.hasFainted()) {
                reward += 800.0; // Opponent pokemon dead.
            }
        }
        if (myPokemon != null && myNxtPokemon != null) {
            if (!myPokemon.hasFainted() && myNxtPokemon.hasFainted()) {
                reward -= 800.0; // My pokemon dead.
            }
        }

        // Pokemon damaging.
        if (oppPokemon != null && oppNxtPokemon != null) {
            double oppHpDiff = oppPokemon.getCurrentStat(Stat.HP) - oppNxtPokemon.getCurrentStat(Stat.HP);
            reward += oppHpDiff * 1.2; // Opponent getting damaged.
        }
        if (myPokemon != null && myNxtPokemon != null) {
            double myHpDiff = myPokemon.getCurrentStat(Stat.HP) - myNxtPokemon.getCurrentStat(Stat.HP);
            reward -= myHpDiff * 1.2; // My pokemon getting damaged.
        }

        // Condition based rewarding.
        if (oppPokemon != null && oppNxtPokemon != null) {
            NonVolatileStatus oppStatus = oppPokemon.getNonVolatileStatus();
            NonVolatileStatus oppNxtStatus = oppNxtPokemon.getNonVolatileStatus();

            if (oppStatus == NonVolatileStatus.NONE && oppNxtStatus != NonVolatileStatus.NONE) {
                reward += getStatusReward(oppNxtStatus); // Opponent getting status condition.
            }
        }

        if (myPokemon != null && myNxtPokemon != null) {
            NonVolatileStatus myStatus = myPokemon.getNonVolatileStatus();
            NonVolatileStatus myNxtStatus = myNxtPokemon.getNonVolatileStatus();

            if (myStatus == NonVolatileStatus.NONE && myNxtStatus != NonVolatileStatus.NONE) {
                reward -= getStatusReward(myNxtStatus); // My pokemon getting status condition.
            }
        }

        reward += calcBoostReward(myPokemon, myNxtPokemon, true);
        reward += calcBoostReward(oppPokemon, oppNxtPokemon, false);

        int myTeamTotHP = getTeamTotHP(myNxtTeam);
        int oppTeamTotHP = getTeamTotHP(oppNxtTeam);
        int prevMyTeamTotHP = getTeamTotHP(myTeam);
        int prevOppTeamTotHP = getTeamTotHP(oppTeam);

        int hpDiff = (prevMyTeamTotHP - myTeamTotHP) - (prevOppTeamTotHP - oppTeamTotHP);
        reward -= hpDiff / 40.0; // Team HP difference, we reward.

        if (action != null && action.getPower() != null) {
            Integer moveP = action.getPower();
            // Plenalty for the bad move.
            if (moveP < 40 && moveP > 0) {
                reward -= 20.0; // Penalize low power moves.
            }
        }

        if (oppPokemon != null && oppNxtPokemon != null) {
            if (!oppPokemon.hasFainted() && oppPokemon != oppNxtPokemon) {
                reward += 150.0; // Forced switch is good
            }
        }

        return reward;
    }

    // Total team hp.
    private int getTeamTotHP(TeamView team) {
        int totHP = 0;
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                totHP += pkmn.getCurrentStat(Stat.HP);
            }
        }
        return totHP;
    }

    private double getStatusReward(NonVolatileStatus status) {
        switch (status) {
            case TOXIC:
                return 120.0; // continuous escalating damage
            case BURN:
                return 90.0; // damage + attack reduction
            case PARALYSIS:
                return 90.0; // speed reduction
            case POISON:
                return 70.0; // continuous damage
            case SLEEP:
                return 100.0; // pokemon can't move for multiple turns
            case FREEZE:
                return 100.0; // similar to sleep
            default:
                return 0.0;
        }
    }

    private double calcBoostReward(PokemonView before, PokemonView after, boolean isMine) {
        if (before == null || after == null) {
            return 0.0;
        }
        double boost = 0.0;

        Stat[] checkStats = {
                Stat.ATK,
                Stat.DEF,
                Stat.SPD,
                Stat.SPATK,
                Stat.SPDEF,
                Stat.ACC,
                Stat.EVASIVE
        };

        for (Stat stat : checkStats) {
            int beforeBoost = before.getStatMultiplier(stat);
            int afterBoost = after.getStatMultiplier(stat);
            int diff = afterBoost - beforeBoost;
            if (diff != 0) {
                double multiplier = 1.0;
                if (stat == Stat.ATK || stat == Stat.SPATK || stat == Stat.SPD) {
                    multiplier = 1.5; // Prioritize offensive/speed stats for sweepers.
                }
                if (isMine) {
                    boost += diff * 25.0 * multiplier; // Reward my boosts.
                } else {
                    boost -= diff * 25.0 * multiplier; // Penalize opponent's boosts.
                }
            }
        }
        return boost;
    }

    // Whole team is dead.
    private boolean teamDead(TeamView team) {
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                return false;
            }
        }
        return true;
    }

}
