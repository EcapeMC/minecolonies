package com.minecolonies.entity.ai.citizen.guard;

import com.minecolonies.colony.jobs.JobGuard;
import com.minecolonies.entity.ai.util.AIState;
import com.minecolonies.entity.ai.util.AITarget;
import com.minecolonies.util.InventoryFunctions;
import com.minecolonies.util.Utils;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.DamageSource;
import net.minecraftforge.client.event.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.entity.ai.util.AIState.*;

/**
 * Handles the AI of the guard entities.
 */
public class EntityAIMeleeGuard extends AbstractEntityAIGuard
{
    /**
     * Basic delay for the next shot.
     */
    private static final int BASE_RELOAD_TIME = 30;

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
    private static final double XP_EACH_HIT = 0.2;

    /**
     * Base speed of the guard he follows his target.
     */
    private static final int BASE_FOLLOW_SPEED = 1;

    /**
     * Base multiplier increasing the attack speed each level.
     */
    private static final double BASE_FOLLOW_SPEED_MULTIPLIER = 0.25D;

    /**
     * The Min distance of the guard to attack entities.
     */
    private static final double MIN_ATTACK_DISTANCE = 2.0D;

    /**
     * Damage per range attack.
     */
    private static final double DAMAGE_PER_ATTACK = 0.5;

    /**
     * Chance that a mob is lit on fire when a weapon has the fire aspect enchantment.
     */
    private static final int FIRE_CHANCE_MULTIPLIER = 4;

    /**
     * Sets up some important skeleton stuff for every ai.
     *
     * @param job the job class
     */
    public EntityAIMeleeGuard(@NotNull final JobGuard job)
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
        if(checkForWeapon())
        {
            return AIState.GUARD_SEARCH_TARGET;
        }

        InventoryFunctions.matchFirstInInventory(worker.getInventoryCitizen(), stack -> stack != null && Utils.doesItemServeAsWeapon(stack), worker::setHeldItem);

        return AIState.GUARD_SEARCH_TARGET;
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
        if(!targetEntity.isEntityAlive() || checkForWeapon())
        {
            targetEntity = null;
        }

        if (targetEntity != null)
        {
            if(worker.getEntitySenses().canSee(targetEntity) && worker.getDistanceToEntity(targetEntity) <= MIN_ATTACK_DISTANCE)
            {
                worker.removeHeldItem();
                attackEntity(targetEntity, (float)DAMAGE_PER_ATTACK);
                setDelay(getReloadTime());
                attacksExecuted += 1;

                if(attacksExecuted >= getMaxAttacksUntilRestock())
                {
                    return AIState.GUARD_RESTOCK;
                }

                return AIState.GUARD_HUNT_DOWN_TARGET;
            }
            worker.setAIMoveSpeed((float) (BASE_FOLLOW_SPEED + BASE_FOLLOW_SPEED_MULTIPLIER * worker.getExperienceLevel()));
            worker.isWorkerAtSiteWithMove(targetEntity.getPosition(), (int) MIN_ATTACK_DISTANCE);

            return AIState.GUARD_SEARCH_TARGET;
        }

        worker.setAIMoveSpeed(1);
        return AIState.GUARD_SEARCH_TARGET;
    }

    private void attackEntity(@NotNull EntityLivingBase entityToAttack, float baseDamage)
    {
        double damgeToBeDealt = baseDamage;

        ItemStack heldItem = worker.getHeldItem();
        if(heldItem != null)
        {
            if(heldItem.getItem() instanceof ItemSword)
            {
                damgeToBeDealt += ((ItemSword) heldItem.getItem()).getDamageVsEntity();
            }
            damgeToBeDealt += EnchantmentHelper.getModifierForCreature(heldItem, targetEntity.getCreatureAttribute());
        }

        targetEntity.attackEntityFrom(new DamageSource(worker.getName()), (float)damgeToBeDealt);
        targetEntity.setRevengeTarget(worker);

        int fireAspectModifier = EnchantmentHelper.getFireAspectModifier(worker);
        if(fireAspectModifier > 0)
        {
            targetEntity.setFire(fireAspectModifier * FIRE_CHANCE_MULTIPLIER);
        }

        worker.addExperience(XP_EACH_HIT);
        worker.faceEntity(entityToAttack, (float)TURN_AROUND, (float)TURN_AROUND);
        worker.getLookHelper().setLookPositionWithEntity(entityToAttack, (float)TURN_AROUND, (float)TURN_AROUND);

        double xDiff = targetEntity.posX - worker.posX;
        double zDiff = targetEntity.posZ - worker.posZ;

        double goToX = xDiff > 0? MOVE_MINIMAL : -MOVE_MINIMAL;
        double goToZ = zDiff > 0? MOVE_MINIMAL : -MOVE_MINIMAL;

        worker.moveEntity(goToX, 0, goToZ);
        
        worker.swingItem();
        worker.playSound("entity.player.attack.sweep", (float)BASIC_VOLUME, (float) getRandomPitch());

        worker.damageItemInHand(1);
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