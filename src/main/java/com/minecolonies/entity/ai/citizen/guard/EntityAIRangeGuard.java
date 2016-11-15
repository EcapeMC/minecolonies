package com.minecolonies.entity.ai.citizen.guard;

import com.minecolonies.colony.jobs.JobGuard;
import com.minecolonies.entity.ai.util.AIState;
import com.minecolonies.entity.ai.util.AITarget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

import static com.minecolonies.entity.ai.util.AIState.*;

/**
 * Handles the AI of the guard entities.
 */
public class EntityAIRangeGuard extends AbstractEntityAIGuard implements IRangedAttackMob
{
    /**
     * Basic delay for the next shot.
     */
    private static final int BASE_RELOAD_TIME = 60;

    /**
     * Base damage which the power enchantments added
     */
    private static final double BASE_POWER_ENCHANTMENT_DAMAGE = 0.5D;

    /**
     * Damage per power enchantment level.
     */
    private static final double POWER_ENCHANTMENT_DAMAGE_MULTIPLIER = 0.5D;

    /**
     * Multiply the base damage always with this.
     */
    private static final double BASE_DAMAGE_MULTIPLIER = 2.0D;

    /**
     * Multiply some random with this to get some random damage addition.
     */
    private static final double RANDOM_DAMAGE_MULTPLIER = 0.25D;

    /**
     * When the difficulty is higher the damage increases by this each level.
     */
    private static final double DIFFICULTY_DAMAGE_INCREASE = 0.11D;

    /**
     * Chance that the arrow lights up the target when the target is on fire.
     */
    private static final int FIRE_EFFECT_CHANCE = 100;

    /**
     * The pitch will be divided by this to calculate it for the arrow sound.
     */
    private static final double PITCH_DIVIDER = 1.0D;

    /**
     * The base pitch, add more to this to change the sound.
     */
    private static final double BASE_PITCH = 0.8D;

    /**
     * Random is multiplied by this to get a random arrow sound.
     */
    private static final double PITCH_MULTIPLIER = 0.4D;

    /**
     * Quantity to be moved to rotate the entity without actually moving.
     */
    private static final double MOVE_MINIMAL = 0.01D;

    /**
     * Quantity the worker should turn around all at once.
     */
    private static final double TURN_AROUND = 180D;

    /**
     * Normal volume at which sounds are played at.
     */
    private static final double BASIC_VOLUME = 1.0D;

    /**
     * Experience the guard receives each shot arrow.
     */
    private static final double XP_EACH_ARROW   = 0.2;

    /**
     * Used to calculate the chance that an arrow hits, if the worker levels is higher than 15 the chance gets worse again.
     * Because of the rising fire speed.
     */
    private static final double HIT_CHANCE_DIVIDER = 15.0D;

    /**
     * The arrow travell speed.
     */
    private static final double ARROW_SPEED = 1.6D;

    /**
     * Base speed of the guard he follows his target.
     */
    private static final int BASE_FOLLOW_SPEED = 1;

    /**
     * Base multiplier increasing the attack speed each level.
     */
    private static final double BASE_FOLLOW_SPEED_MULTIPLIER = 0.25D;

    /**
     * The start search distance of the guard to track/attack entities may get more depending on the level.
     */
    private static final double MAX_ATTACK_DISTANCE = 20.0D;

    /**
     * Damage per range attack.
     */
    private static final int DAMAGE_PER_ATTACK = 2;

    /**
     * When target is out of sight, try to move that close to the target.
     */
    private static final int MOVE_CLOSE = 3;

    /**
     * Sets up some important skeleton stuff for every ai.
     *
     * @param job the job class
     */
    public EntityAIRangeGuard(@NotNull final JobGuard job)
    {
        super(job);
        super.registerTargets(
                new AITarget(GUARD_SEARCH_TARGET, this::searchTarget),
                new AITarget(GUARD_GET_TARGET, this::getTarget),
                new AITarget(GUARD_HUNT_DOWN_TARGET, this::huntDown),
                new AITarget(GUARD_PATROL, this::patrol),
                new AITarget(GUARD_RESTOCK, this::goToBuilding)
        );

        if(worker.getCitizenData() != null)
        {
            worker.setSkillModifier(2 * worker.getCitizenData().getIntelligence() + worker.getCitizenData().getStrength());
            worker.setCanPickUpLoot(true);
        }
    }

    @Override
    protected AIState searchTarget()
    {
        if(checkOrRequestItems(new ItemStack(Items.bow)))
        {
            return AIState.GUARD_SEARCH_TARGET;
        }
        worker.setHeldItem(worker.findFirstSlotInInventoryWith(Items.bow));
        return super.searchTarget();
    }

    private int getReloadTime()
    {
        return BASE_RELOAD_TIME / (worker.getExperienceLevel() + 1);
    }

    /**
     * Follow the target and kill it.
     * @return the next AIState.
     */
    protected AIState huntDown()
    {
        if(!targetEntity.isEntityAlive() || checkOrRequestItems(new ItemStack(Items.bow)))
        {
            targetEntity = null;
            worker.setAIMoveSpeed((float) 1.0D);
            return AIState.GUARD_GATHERING;
        }

        if (worker.getEntitySenses().canSee(targetEntity) && worker.getDistanceToEntity(targetEntity) <= MAX_ATTACK_DISTANCE)
        {
            if(worker.getEntitySenses().canSee(targetEntity) && worker.getDistanceToEntity(targetEntity) <= MAX_ATTACK_DISTANCE)
            {
                attackEntityWithRangedAttack(targetEntity, DAMAGE_PER_ATTACK);
                setDelay(getReloadTime());
                attacksExecuted += 1;

                if(attacksExecuted >= getMaxAttacksUntilRestock())
                {
                    return AIState.GUARD_RESTOCK;
                }

                return AIState.GUARD_HUNT_DOWN_TARGET;
            }

            return AIState.GUARD_HUNT_DOWN_TARGET;
        }
        worker.setAIMoveSpeed((float) (BASE_FOLLOW_SPEED + BASE_FOLLOW_SPEED_MULTIPLIER * worker.getExperienceLevel()));
        worker.isWorkerAtSiteWithMove(targetEntity.getPosition(), MOVE_CLOSE);

        return AIState.GUARD_SEARCH_TARGET;
    }

    @Override
    public void attackEntityWithRangedAttack(EntityLivingBase entityToAttack, float baseDamage)
    {
        double chance = HIT_CHANCE_DIVIDER / (worker.getExperienceLevel()+1);
        EntityArrow arrowEntity = new EntityArrow(worker.worldObj, worker, entityToAttack, (float) ARROW_SPEED, (float)chance);
        int powerEnchantment = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, worker.getHeldItem());
        int punchEnchantment = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, worker.getHeldItem());
        arrowEntity.setDamage((baseDamage * BASE_DAMAGE_MULTIPLIER)
                + new Random().nextGaussian() * RANDOM_DAMAGE_MULTPLIER
                + (float)worker.worldObj.getDifficulty().getDifficultyId() * DIFFICULTY_DAMAGE_INCREASE);
        if(powerEnchantment > 0)
        {
            arrowEntity.setDamage(arrowEntity.getDamage() + (double)powerEnchantment * POWER_ENCHANTMENT_DAMAGE_MULTIPLIER + BASE_POWER_ENCHANTMENT_DAMAGE);
        }

        if(punchEnchantment > 0)
        {
            arrowEntity.setKnockbackStrength(punchEnchantment);
        }

        if(EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, worker.getHeldItem()) > 0)
        {
            arrowEntity.setFire(FIRE_EFFECT_CHANCE);
        }

        worker.addExperience(XP_EACH_ARROW);
        worker.faceEntity(entityToAttack, (float)TURN_AROUND, (float)TURN_AROUND);
        worker.getLookHelper().setLookPositionWithEntity(entityToAttack, (float)TURN_AROUND, (float)TURN_AROUND);

        double xDiff = targetEntity.posX - worker.posX;
        double zDiff = targetEntity.posZ - worker.posZ;

        double goToX = xDiff > 0? MOVE_MINIMAL : -MOVE_MINIMAL;
        double goToZ = zDiff > 0? MOVE_MINIMAL : -MOVE_MINIMAL;

        worker.swingItem();
        worker.moveEntity(goToX, 0, goToZ);
        worker.playSound("random.bow", (float)BASIC_VOLUME, (float) getRandomPitch());
        worker.worldObj.spawnEntityInWorld(arrowEntity);
    }

    /**
     * Calculates a random pitch for the arrow sound.
     * @return the random pitch as a double.
     */
    private double getRandomPitch()
    {
        return PITCH_DIVIDER / (worker.getRNG().nextDouble() * PITCH_MULTIPLIER + BASE_PITCH);
    }
}