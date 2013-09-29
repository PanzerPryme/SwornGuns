package net.dmulloy2.swornguns.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import lombok.Data;
import net.dmulloy2.swornguns.SwornGuns;
import net.dmulloy2.swornguns.util.InventoryHelper;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * @author dmulloy2
 */

@Data
public class GunPlayer
{
	private int ticks;
	private Player controller;
	private ItemStack lastHeldItem;
	private List<Gun> guns;
	private Gun currentlyFiring;
	private boolean enabled;

	private final SwornGuns plugin;

	public GunPlayer(SwornGuns plugin, Player player)
	{
		this.plugin = plugin;
		this.controller = player;
		this.enabled = true;
		
		calculateGuns();
	}

	public boolean isAimedIn()
	{
		return (controller != null && controller.isOnline() && controller.hasPotionEffect(PotionEffectType.SLOW));
	}

	public void onClick(String clickType)
	{
		ItemStack inHand = controller.getItemInHand();
		if (inHand == null || inHand.getType() == Material.AIR)
		{
			return;
		}

		List<Gun> gunsCanFire = getGunsByType(inHand);
		if (gunsCanFire.isEmpty())
		{
			return;
		}

		Gun gun = null;
		boolean canFire = false;
		for (Gun g : gunsCanFire)
		{
			if (plugin.getPermissionHandler().canFireGun(controller, g))
			{
				canFire = true;
				gun = g;
				break;
			}
		}

		if (gun == null)
		{
			return;
		}

		if (clickType.equalsIgnoreCase("right"))
		{
			if (gun.isCanClickRight() || gun.isCanAimRight())
			{
				if (! gun.isCanAimRight())
				{
					gun.setHeldDownTicks(gun.getHeldDownTicks() + 1);
					gun.setLastFired(0);
					if (currentlyFiring == null)
					{
						fireGun(gun, canFire);
					}
				}
				else
				{
					checkAim(canFire);
				}
			}
		}
		else if (clickType.equalsIgnoreCase("left"))
		{
			if (gun.isCanClickLeft() || gun.isCanAimLeft())
			{
				if (! gun.isCanAimLeft())
				{
					gun.setHeldDownTicks(gun.getHeldDownTicks() + 1);
					gun.setLastFired(0);
					if (currentlyFiring == null)
					{
						fireGun(gun, canFire);
					}
				}
				else
				{
					checkAim(canFire);
				}
			}
		}
	}

	protected void checkAim(boolean canFire)
	{
		if (!canFire)
		{
			return;
		}

		if (isAimedIn())
		{
			controller.removePotionEffect(PotionEffectType.SLOW);
		}
		else
		{
			controller.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 12000, 4));
		}
	}

	private void fireGun(Gun gun, boolean canFire)
	{
		if (!canFire)
		{
			controller.sendMessage(ChatColor.RED + "You cannot fire this gun!");
			return;
		}

		if (gun.getTimer() <= 0)
		{
			this.currentlyFiring = gun;
			
			gun.setFiring(true);
		}
	}

	public void tick()
	{
		this.ticks++;
		
		if (controller != null)
		{
			ItemStack hand = controller.getItemInHand();
			
			this.lastHeldItem = hand;
			
			if (ticks % 10 == 0 && hand != null)
			{
				if (plugin.getGun(hand.getType()) == null)
				{
					controller.removePotionEffect(PotionEffectType.SLOW);
				}
			}
			
			for (Gun g : guns)
			{
				if (g != null)
				{
					g.tick();

					if (controller.isDead())
					{
						g.finishReloading();
					}
					
					if (hand != null && g.getGunMaterial() == hand.getType() && isAimedIn() && ! g.isCanAimLeft() && ! g.isCanAimRight())
					{
						controller.removePotionEffect(PotionEffectType.SLOW);
					}
					
					if (currentlyFiring != null && g.getTimer() <= 0 && currentlyFiring.equals(g))
						this.currentlyFiring = null;
				}
			}
		}
		
		renameGuns(controller);
	}

	protected void renameGuns(Player p)
	{
		Inventory inv = p.getInventory();
		ItemStack[] items = inv.getContents();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i] != null)
			{
				String name = getGunName(items[i]);
				if (name != null && ! name.isEmpty())
				{
					setName(items[i], name);
				}
			}
		}
	}

	protected List<Gun> getGunsByType(ItemStack item)
	{
		List<Gun> ret = new ArrayList<Gun>();
		for (Gun gun : guns)
		{
			if (gun.getGunMaterial() == item.getType())
			{
				ret.add(gun);
			}
		}

		return ret;
	}

	protected String getGunName(ItemStack item)
	{
		String ret = "";
		List<Gun> tempgun = getGunsByType(item);
		int amtGun = tempgun.size();
		if (amtGun > 0)
		{
			for (Gun current : tempgun)
			{
				if (plugin.getPermissionHandler().canFireGun(controller, current))
				{
					if (current.getGunMaterial() != null && current.getGunMaterial() == item.getType())
					{
						byte gunDat = current.getGunByte();
						
						@SuppressWarnings("deprecation") // TODO
						byte itmDat = item.getData().getData();

						if ((gunDat == itmDat) || (current.isIgnoreItemData()))
						{
							return getGunName(current);
						}
					}
				}
			}
		}
		return ret;
	}

	private String getGunName(Gun current)
	{
		String add = "";
		String refresh = "";
		if (current.isHasClip())
		{
			int leftInClip = 0;
			int ammoLeft = 0;
			int maxInClip = current.getMaxClipSize();

			int currentAmmo = (int) Math.floor(InventoryHelper.amtItem(this.controller.getInventory(), current.getAmmoType(),
					current.getAmmoByte())
					/ current.getAmmoAmtNeeded());
			ammoLeft = currentAmmo - maxInClip + current.getRoundsFired();
			if (ammoLeft < 0)
				ammoLeft = 0;
			leftInClip = currentAmmo - ammoLeft;
			add = ChatColor.YELLOW + "    \u00AB" + Integer.toString(leftInClip) + " \uFFE8 " + Integer.toString(ammoLeft) + "\u00BB";
			if (current.isReloading())
			{
				int reloadSize = 4;
				double reloadFrac = (current.getReloadTime() - current.getGunReloadTimer()) / current.getReloadTime();
				int amt = (int) Math.round(reloadFrac * reloadSize);
				for (int ii = 0; ii < amt; ii++)
				{
					refresh = refresh + "\u25AA";
				}
				for (int ii = 0; ii < reloadSize - amt; ii++)
				{
					refresh = refresh + "\u25AB";
				}

				add = ChatColor.RED + "    " + new StringBuffer(refresh).reverse() + "RELOADING" + refresh;
			}
		}
		
		return current.getName() + add;
	}

	protected ItemStack setName(ItemStack item, String name)
	{
		ItemMeta im = item.getItemMeta();
		im.setDisplayName(name);
		item.setItemMeta(im);

		return item;
	}

	public Player getPlayer()
	{
		return this.controller;
	}

	public void unload()
	{
		this.controller = null;
		this.currentlyFiring = null;

		for (Gun gun : guns)
		{
			gun.clear();
		}
	}

	public void reloadAllGuns()
	{
		for (Gun gun : guns)
		{
			if (gun != null)
			{
				gun.reloadGun();
				gun.finishReloading();
			}
		}
	}

	public boolean checkAmmo(Gun gun, int amount)
	{
		return InventoryHelper.amtItem(this.controller.getInventory(), gun.getAmmoType(), gun.getAmmoByte()) >= amount;
	}

	public void removeAmmo(Gun gun, int amount)
	{
		if (amount == 0)
		{
			return;
		}

		InventoryHelper.removeItem(controller.getInventory(), gun.getAmmoType(), gun.getAmmoByte(), amount);
	}

	public ItemStack getLastItemHeld()
	{
		return this.lastHeldItem;
	}

	public Gun getGun(Material material)
	{
		for (Gun check : guns)
		{
			if (check.getGunMaterial() == material)
				return check;
		}

		return null;
	}
	
	public void calculateGuns()
	{
		List<Gun> loadedGuns = new ArrayList<Gun>();
		
		for (Gun gun : plugin.getLoadedGuns())
		{
			if (plugin.getPermissionHandler().canFireGun(controller, gun))
				loadedGuns.add(gun);
		}
		
		HashMap<Material, List<Gun>> map1 = new HashMap<Material, List<Gun>>();
		
		for (Gun gun : loadedGuns)
		{
			if (! map1.containsKey(gun.getGunMaterial()))
			{
				map1.put(gun.getGunMaterial(), new ArrayList<Gun>());
			}

			map1.get(gun.getGunMaterial()).add(gun);
		}

		List<Gun> sortedMap = new ArrayList<Gun>();
		
		for (Entry<Material, List<Gun>> entry : map1.entrySet())
		{
			HashMap<Gun, Integer> priorityMap = new HashMap<Gun, Integer>();
			for (Gun gun : entry.getValue())
			{
				priorityMap.put(gun, gun.getPriority());
			}
			
			List<Entry<Gun, Integer>> sortedEntries = new ArrayList<Entry<Gun, Integer>>(priorityMap.entrySet());
			Collections.sort(sortedEntries, new Comparator<Entry<Gun, Integer>>()
			{
				@Override
				public int compare(final Entry<Gun, Integer> entry1, final Entry<Gun, Integer> entry2)
				{
					return -entry1.getValue().compareTo(entry2.getValue());
				}
			});
			
			for (Entry<Gun, Integer> entry1 : sortedEntries)
			{
				sortedMap.add(entry1.getKey());
				break;
			}
		}
		
		this.guns = sortedMap;
		
		// Clear out stuff
		loadedGuns.clear();
		loadedGuns = null;
		
		map1.clear();
		map1 = null;
		
		sortedMap.clear();
		sortedMap = null;
	}
}