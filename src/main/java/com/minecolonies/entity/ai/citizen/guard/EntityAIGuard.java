package com.minecolonies.entity.ai.citizen.guard;

import com.minecolonies.colony.jobs.JobGuard;
import com.minecolonies.entity.ai.basic.AbstractEntityAISkill;
import com.minecolonies.entity.ai.minimal.EntityAICitizenAvoidEntity;
import com.minecolonies.entity.ai.util.AIState;
import com.minecolonies.entity.ai.util.AITarget;
import com.minecolonies.util.InventoryUtils;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;

import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.*;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.minecolonies.entity.ai.util.AIState.*;


/**
 * Handles the AI of the guard entities.
 */
public class EntityAIGuard extends AbstractEntityAISkill<JobGuard> implements IRangedAttackMob
{
    private static final double MAX_ATTACK_DISTANCE = 20.0D;
    EntityLivingBase targetEntity;
    private int searchDistance = 5;
    List entityList;
    /**
     * Sets up some important skeleton stuff for every ai.
     *
     * @param job the job class
     */
    public EntityAIGuard(@NotNull final JobGuard job)
    {
        super(job);
        super.registerTargets(
                new AITarget(IDLE, () -> START_WORKING),
                new AITarget(START_WORKING, () -> GUARD_SEARCH_TARGET),
                new AITarget(GUARD_SEARCH_TARGET, this::searchTarget),
                new AITarget(GUARD_GET_TARGET, this::getTarget),
                new AITarget(GUARD_HUNT_DOWN_TARGET, this::huntDown)
                );
        worker.setSkillModifier(2 * worker.getCitizenData().getIntelligence() + worker.getCitizenData().getStrength());
        worker.setCanPickUpLoot(true);
    }

    /**
     * Can be overridden in implementations.
     * <p>
     * Here the AI can check if the fishes or rods have to be re rendered and do it.
     */
    @Override
    protected void updateRenderMetaData()
    {
        updateArmor();
    }

    /**
     * Updates the equipment. Always take the first item of each type and set it.
     */
    private void updateArmor()
    {
        worker.setItemStackToSlot(EntityEquipmentSlot.CHEST, null);
        worker.setItemStackToSlot(EntityEquipmentSlot.FEET, null);
        worker.setItemStackToSlot(EntityEquipmentSlot.HEAD, null);
        worker.setItemStackToSlot(EntityEquipmentSlot.LEGS, null);

        for(int i = 0; i < worker.getInventoryCitizen().getSizeInventory(); i++)
        {
            ItemStack stack = worker.getInventoryCitizen().getStackInSlot(i);

            if(stack == null || stack.getItem() == null)
            {
                continue;
            }
            
            if(stack.getItem() instanceof ItemArmor && worker.getItemStackFromSlot(((ItemArmor) stack.getItem()).getEquipmentSlot()) == null)
            {
                worker.setItemStackToSlot(((ItemArmor) stack.getItem()).getEquipmentSlot(), stack);
            }
        }
    }

    /**
     * Chooses a target from the list.
     * @return the next state.
     */
    private AIState getTarget()
    {
        if(entityList.isEmpty())
        {
            return AIState.GUARD_SEARCH_TARGET;
        }
        if (!worker.getEntitySenses().canSee((EntityLivingBase) entityList.get(0)) || !((EntityLivingBase) entityList.get(0)).isEntityAlive())
        {
            entityList.remove(0);
            setDelay(10);
            return AIState.GUARD_GET_TARGET;
        }
        else
        {
            targetEntity = (EntityLivingBase) entityList.get(0);
            return AIState.GUARD_HUNT_DOWN_TARGET;
        }
    }

    /**
     * Searches for the next taget.
     * @return the next AIState.
     */
    private AIState searchTarget()
    {
        if (worker == null)
        {
            return AIState.GUARD_SEARCH_TARGET;
        }

        entityList = this.worker.worldObj.getEntitiesWithinAABB(EntityMob.class, this.getTargetableArea(searchDistance));
        entityList.addAll(this.worker.worldObj.getEntitiesWithinAABB(EntitySlime.class, this.getTargetableArea(searchDistance)));

        if(!worker.hasItemInInventory(Items.BOW))
        {
            return AIState.GUARD_SEARCH_TARGET;
        }
        worker.setHeldItem(worker.findFirstSlotInInventoryWith(Items.BOW));

        setDelay(10);
        if(entityList.isEmpty())
        {
            if(searchDistance < MAX_ATTACK_DISTANCE)
            {
                searchDistance += 5;
            }

            return AIState.GUARD_SEARCH_TARGET;
        }
        return AIState.GUARD_GET_TARGET;
    }

    /**
     * Follow the target and kill it.
     * @return the next AIState.
     */
    private AIState huntDown()
    {
        if(!targetEntity.isEntityAlive())
        {
            targetEntity = null;
        }

        if (targetEntity != null)
        {
            if(worker.getEntitySenses().canSee(targetEntity))
            {
                worker.resetActiveHand();
                attackEntityWithRangedAttack(targetEntity, 1);
                setDelay(100);
                return AIState.GUARD_HUNT_DOWN_TARGET;
            }
            worker.isWorkerAtSiteWithMove(targetEntity.getPosition(), 5);
            return AIState.GUARD_SEARCH_TARGET;
        }

        return AIState.GUARD_SEARCH_TARGET;
    }


    @Override
    public void attackEntityWithRangedAttack(@NotNull EntityLivingBase entityToAttack, float baseDamage)
    {
        EntityTippedArrow arrowEntity = new EntityTippedArrow(this.worker.worldObj, worker);
        double xVector = entityToAttack.posX - worker.posX;
        double yVector = entityToAttack.getEntityBoundingBox().minY + (double)(entityToAttack.height / 3.0F) - arrowEntity.posY;
        double zVector = entityToAttack.posZ - worker.posZ;
        double distance = (double) MathHelper.sqrt_double(xVector * xVector + zVector * zVector);
        arrowEntity.setThrowableHeading(xVector, yVector + distance * 0.20000000298023224D, zVector, 1.6F, (float)(14 - this.worker.worldObj.getDifficulty().getDifficultyId() * 4));
        int powerEntchanment = EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.POWER, worker);
        int punchEntchanment = EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.PUNCH, worker);
        DifficultyInstance difficulty = this.worker.worldObj.getDifficultyForLocation(new BlockPos(worker));
        arrowEntity.setDamage((baseDamage * 2.0D)
                + worker.getRandom().nextGaussian() * 0.25D
                + this.worker.worldObj.getDifficulty().getDifficultyId() * 0.11D);
        if(powerEntchanment > 0)
        {
            arrowEntity.setDamage(arrowEntity.getDamage() + (double)powerEntchanment * 0.5D + 0.5D);
        }

        if(punchEntchanment > 0)
        {
            arrowEntity.setKnockbackStrength(punchEntchanment);
        }

        boolean onFire = worker.isBurning() && difficulty.func_190083_c() && worker.getRandom().nextBoolean();
        onFire = onFire || EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.FLAME, worker) > 0;

        if(onFire)
        {
            arrowEntity.setFire(100);
        }

        ItemStack holdItem = worker.getHeldItem(EnumHand.OFF_HAND);
        if(holdItem != null && holdItem.getItem() == Items.TIPPED_ARROW)
        {
            arrowEntity.setPotionEffect(holdItem);
        }

        worker.faceEntity(entityToAttack, 30.0F, 30.0F);
        worker.getLookHelper().setLookPositionWithEntity(entityToAttack, 30.0F, 30.0F);
        worker.setMoveForward(10);

        worker.swingArm(EnumHand.MAIN_HAND);
        worker.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (worker.getRNG().nextFloat() * 0.4F + 0.8F));
        worker.worldObj.spawnEntityInWorld(arrowEntity);
    }

    protected AxisAlignedBB getTargetableArea(double range)
    {
        return this.worker.getEntityBoundingBox().expand(range, 4.0D, range);
    }

}
