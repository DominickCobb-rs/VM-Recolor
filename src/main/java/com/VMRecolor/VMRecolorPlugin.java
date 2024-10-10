/*
	BSD 2-Clause License
	Copyright (c) 2018, Adam <Adam@sigterm.info>
	Copyright (c) 2018, James Swindle <wilingua@gmail.com>
	Copyright (c) 2024, denaelc

	Redistribution and use in source and binary forms, with or without
	modification, are permitted provided that the following conditions are met:

	1. Redistributions of source code must retain the above copyright notice, this
	   list of conditions and the following disclaimer.

	2. Redistributions in binary form must reproduce the above copyright notice,
	   this list of conditions and the following disclaimer in the documentation
	   and/or other materials provided with the distribution.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
	AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
	IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
	DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
	FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
	DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
	SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
	CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
	OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
	OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// A lot of code used from the gauntlet recolor plugin.
// Menus were derived from Adam's in core RuneLite.

package com.VMRecolor;

import com.VMRecolor.VMRecolorConfig.BoulderTypes;
import com.VMRecolor.VMRecolorConfig.GlobalColor;
import com.VMRecolor.VMRecolorConfig.LavaOptions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.applet.Applet;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;

@Slf4j
@PluginDescriptor(name = "VM-Recolor", enabledByDefault = true)
public class VMRecolorPlugin extends Plugin
{
	public static final String CONFIG_GROUP = "VMRecolor";
	@Inject
	private Client client;

	@Inject
	private VMRecolorConfig config;

	@Inject
	private ModelRecolorer modelRecolorer;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ColorPickerManager colorPickerManager;

	@Inject
	private ConfigManager configManager;

	private final ArrayList<GameObject> recordedGameObjects = new ArrayList<>();
	private final ArrayList<GroundObject> recordedGroundObjects = new ArrayList<>();
	private final ArrayList<Model> recordedModels = new ArrayList<>();
	private final ArrayList<NPC> recordedNpcs = new ArrayList<>();
	private final ArrayList<Projectile> recordedProjectiles = new ArrayList<>();
	private final ArrayList<GraphicsObject> recordedGraphicsObjects = new ArrayList<>();
	private final ArrayList<Integer> sceneIDs = new ArrayList<>();
	private final Set<Integer> VMRegionIDs = ImmutableSet.of(15263, 15262, 15519, 15518, 15775, 15774);

	private static final Set<String> COLOR_CONFIG_KEYS = ImmutableSet.of("BoulderCustomColor", "wallCustomColor", "lavaBeastCustomColor", "upperLevelFloorCustomColor", "lowerLevelFloorCustomColor", "platformCustomColor", "lavaColor");
	public static final Set<Integer> THE_BOULDER = ImmutableSet.of(31034, 31035, 31036, 31037, 31038);
	public static final Set<Integer> THE_BOULDER_NPCS = ImmutableSet.of(7807, 7808, 7809, 7810, 7811, 7812, 7813, 7814, 7815, 7816);

	public static final Set<Integer> LAVA = ImmutableSet.of(31001, 31002, 31039);
	private static final Set<Integer> GAME_OBJECTS = ImmutableSet.of(30998, 30999, 31000, 31002, 31003, 31004, 31005, 31006, 31007, 31008, 31009, 31010, 31011, 31012, 31013, 30996, 30995, 30994, 30993, 30992, 30991, 30990, 31039, 31014, 31015, 31016, 31017, 31018, 31019, 31020, 31021, 31022, 31023, 31024, 31025, 31026, 31027, 31028, 31029, 31030, 31042, 31043, 31044, 31045, 31046, 31047, 31048, 31049, 31050, 31051, 31052);
	public static final Set<Integer> LOWER_LEVEL_FLOOR = ImmutableSet.of(31003, 31004, 31005, 31006, 31007, 31008, 31009, 31010, 31011, 31012, 31013, 31026, 31028, 31030, 31033, 31042, 31043, 31044, 31045, 31046, 31047, 31048, 31049, 31050, 31051, 31052);
	public static final Set<Integer> LOWER_LEVEL_INTERACTABLES = ImmutableSet.of(31042, 31043, 31044, 31045, 31046, 31047, 31048, 31049, 31050, 31051, 31052);
	public static final Set<Integer> UPPER_LEVEL_FLOOR = ImmutableSet.of(31014, 31015, 31016, 31017, 31018, 31019, 31020, 31021, 31022, 31023, 31024, 31025, 31027);
	public static final Set<Integer> PLATFORMS = ImmutableSet.of(30998, 30999, 31000);
	public static final Set<Integer> WALL_OBJECTS = ImmutableSet.of(30996, 30995, 30994, 30993, 30992, 30991, 30990);
	public static final Set<Integer> GRAPHICS_OBJECTS = ImmutableSet.of(1406, 1407);

	public static final int LAVA_BEAST = 7817;
	private boolean syncingColors = false;

	private int regionId;
	private boolean counting;
	private int ticksSinceInVM;

	@Override
	protected void startUp() throws Exception
	{
		modelRecolorer.cacheData("/model_facecolors.txt");
		modelRecolorer.recolorData();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
			if (VMRegionIDs.contains(regionId) && client.getGameState() == GameState.LOGGED_IN)
			{
				clientThread.invokeLater(() -> client.setGameState(GameState.LOADING));
			}
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invokeLater(() -> {
			clearAll();

			synchronized (modelRecolorer)
			{
				modelRecolorer.cleanUp();
			}
		});
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> {
				client.setGameState(GameState.LOADING);
			});
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!inVMRegion() || !client.isKeyPressed(KeyCode.KC_SHIFT) || (event.getType() != MenuAction.EXAMINE_OBJECT.getId() && event.getMenuEntry().getType() != MenuAction.EXAMINE_NPC))
		{
			return;
		}
		NPC npc = null;
		if (event.getMenuEntry().getType() != MenuAction.EXAMINE_NPC)
		{
			npc = event.getMenuEntry().getNpc();
		}
		TileObject tileObject = null;
		if (event.getType() == MenuAction.EXAMINE_OBJECT.getId())
		{
			tileObject = findTileObject(client.getPlane(), event.getActionParam0(), event.getActionParam1(), event.getIdentifier());
			if (tileObject == null && npc == null)
			{
				return;
			}
		}


		int idx = -1;
		if (tileObject != null && (THE_BOULDER.contains(tileObject.getId()) || GAME_OBJECTS.contains(tileObject.getId())))
		{
			idx = createColorMenu(idx, event.getTarget(), tileObject.getId());
		}
		if (npc != null && (npc.getId() == LAVA_BEAST || THE_BOULDER_NPCS.contains(npc.getId())))
		{
			idx = createColorMenu(idx, event.getTarget(), npc.getId());
		}

	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP) || syncingColors) // Can create loop of death if changing colors without this
		{
			return;
		}
		if (config.syncColors() && COLOR_CONFIG_KEYS.contains(event.getKey()))
		{
			syncingColors = true;
			log.debug("Attmepting to sync color change: {}", event.getKey());
			Color newColor = Color.WHITE;
			switch (event.getKey())
			{

				case "BoulderCustomColor":
				{
					newColor = config.boulderColor();
					break;
				}
				case "wallCustomColor":
				{
					newColor = config.wallColor();
					break;
				}
				case "lavaBeastCustomColor":
				{
					newColor = config.lavaBeastColor();
					break;
				}
				case "upperLevelFloorCustomColor":
				{
					newColor = config.upperLevelFloorColor();
					break;
				}
				case "lowerLevelFloorCustomColor":
				{
					newColor = config.lowerLevelFloorColor();
					break;
				}
				case "platformCustomColor":
				{
					newColor = config.platformColor();
					break;
				}
				case "lavaColor":
				{
					newColor = config.lavaColor();
					break;
				}
			}
			for (String key : COLOR_CONFIG_KEYS)
			{
				log.debug("Syncing color change: {}", key);
				configManager.setConfiguration(CONFIG_GROUP, key, newColor);
			}
			syncingColors = false;

			synchronized (modelRecolorer)
			{
				modelRecolorer.recolorData();
			}

			if (inVMRegion())
			{
				recolorAllNPCs();
				forceReload();
			}
			return;
		}

		switch (event.getKey())
		{
			case "Boulder":
			case "BoulderCustomColor":
			case "lava":
			case "lavaColor":
			case "wall":
			case "wallCustomColor":
			case "platform":
			case "upperLevelFloor":
			case "lowerLevelFloor":
			case "platformCustomColor":
			case "upperLevelFloorCustomColor":
			case "lowerLevelFloorCustomColor":
			{
				synchronized (modelRecolorer)
				{
					modelRecolorer.recolorData();
				}
				if (inVMRegion())
				{
					forceReload();
				}
				break;
				// TODO: Figure out how to recolor walls without forcing scene reload
				// Don't think it can be done simply.
				// Currently all methods of recoloring walls are unsuccessful because other methods apply colors to
				//  *default* models. Because walls have two renderables and there's no way to identify the second renderable
				// they currently have their colors applied directly to the model with no way to recover the initial colors.
				// Potential workaround to forcing client reload is *undo* color applications to the model and reapply?
				// Furthermore, several models used as walls are also used as decorations, as seen on the first floor by the west staircase
				// as well as the platform just under the west stairs.
			}
			case "lavaBeast":
			case "lavaBeastCustomColor":
			{
				synchronized (modelRecolorer)
				{
					modelRecolorer.recolorData();
				}
				recolorAllNPCs();
				break;
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (!inVMRegion())
		{
			return;
		}

		int ID = event.getGameObject().getId();
		if (GAME_OBJECTS.contains(ID) || THE_BOULDER.contains(ID) || WALL_OBJECTS.contains(ID) || LAVA.contains(ID))
		{
			recordedGameObjects.add(event.getGameObject());
			recolorGameObject(event.getGameObject());
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		// Running clearAll on varbit changed is tricky because it
		// crashes the client without waiting a tick after.
		// The vm varbit changes before the player loads the environment outside VM
		if(event.getNpc().getId()==7776 && !recordedGameObjects.isEmpty())
		{
			clientThread.invokeLater(this::clearAll);
			return;
		}
		if (!inVMRegion())
		{
			return;
		}
		if (LAVA_BEAST == event.getNpc().getId())
		{
			recordedNpcs.add(event.getNpc());
			if (config.lavaBeast() == GlobalColor.Default)
			{
				return;
			}
			recolorNPC(event.getNpc());
		}
		if (THE_BOULDER_NPCS.contains(event.getNpc().getId()) && config.boulder() != BoulderTypes.Default)
		{
			recolorNPC(event.getNpc());
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (!inVMRegion())
		{
			return;
		}
		if (LAVA_BEAST == event.getNpc().getId())
		{
			recordedNpcs.add(event.getNpc());
			if (config.lavaBeast() == GlobalColor.Default)
			{
				return;
			}
			recolorNPC(event.getNpc());
		}
		if (THE_BOULDER_NPCS.contains(event.getNpc().getId()) && config.boulder() != BoulderTypes.Default)
		{
			recolorNPC(event.getNpc());
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (!inVMRegion())
		{
			return;
		}
		if (config.lava() == LavaOptions.Default && config.brightness() == 100)
		{
			return;
		}
		int ID = event.getGroundObject().getId();
		if (LAVA.contains(ID) || ID == 659) // 659 is lava projectile splat effect
		{
			recordedGroundObjects.add(event.getGroundObject());
			recolorGroundObject(event.getGroundObject());
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (!inVMRegion())
		{
			return;
		}
		int ID = event.getGraphicsObject().getId();

		if (ID == 659 || GRAPHICS_OBJECTS.contains(ID))
		{
			recordedGraphicsObjects.add(event.getGraphicsObject());
			recolorGraphicsObject(event.getGraphicsObject());
		}
	}

	@Subscribe
	void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (!inVMRegion())
		{
			return;
		}
		if (WALL_OBJECTS.contains(event.getWallObject().getId()))
		{
			recolorWallObject(event.getWallObject());
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved projectileMoved)
	{
		if (!inVMRegion())
		{
			return;
		}
		if (config.lava() != LavaOptions.Default)
		{

			if (projectileMoved.getProjectile().getId() == 660)
			{
				recordedProjectiles.add(projectileMoved.getProjectile());
				recolorProjectile(projectileMoved.getProjectile());
				return;
			}
		}
		if (projectileMoved.getProjectile().getId() == 1403 && config.lavaBeast() != GlobalColor.Default)
		{
			recordedProjectiles.add(projectileMoved.getProjectile());
			recolorProjectile(projectileMoved.getProjectile());
		}
	}

	private boolean inVMRegion()
	{
		regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
		return VMRegionIDs.contains(regionId);
	}

	public void recolorGraphicsObject(GraphicsObject graphicsObject)
	{

		Model model = graphicsObject.getModel();
		if (model == null)
		{
			log.debug("recolorProjectile returned null!");
			return;
		}
		modelRecolorer.applyColors(graphicsObject.getId(), "GraphicsObject", model, config.lava() != LavaOptions.Default);
	}

	public void recolorProjectile(Projectile projectile)
	{
		Model model = projectile.getModel();
		if (model == null)
		{
			log.debug("recolorProjectile returned null!");
			return;
		}
		modelRecolorer.applyColors(projectile.getId(), "Projectile", model, true);
	}

	private void forceReload()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> client.setGameState(GameState.LOADING));
		}
	}

	public void recolorGameObject(GameObject gameObject)
	{
		Renderable renderable = gameObject.getRenderable();
		Model model = verifyModel(renderable);
		if (model == null)
		{
			log.debug("recolorGameObject returned null!");
			return;
		}
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		if (THE_BOULDER.contains(gameObject.getId()))
		{
			modelRecolorer.applyColors(gameObject.getId(), "GameObject", model, config.boulder() != BoulderTypes.Default);
		}
		// Some objects have many different sets of colors on their faces.
		// This makes it non-viable to store *all* variants of their facecolors in a file.
		// Walls and upper level floors in particular have this problem.
		// This circumvents finding colors in a cache and instead applies them directly to the model.
		// As a result, this requires forcing the client into the loading state to restore the original values.
		else if ((gameObject.getId() <= 30992 && gameObject.getId() >= 30990))
		{
			modelRecolorer.applyColorsDirectly(model, 0);
		}
		else if (UPPER_LEVEL_FLOOR.contains(gameObject.getId()))
		{
			modelRecolorer.applyColorsDirectly(model, gameObject.getId());
		}
		else
		{
			modelRecolorer.applyColors(gameObject.getId(), "GameObject", model, true);
		}
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);
	}

	public void recolorGroundObject(GroundObject groundObject)
	{
		Renderable renderable = groundObject.getRenderable();
		Model model = verifyModel(renderable);
		if (model == null)
		{
			log.debug("recolorGroundObject returned null!");
			return;
		}

		modelRecolorer.applyColors(groundObject.getId(), "GroundObject", model, true);
		addToLists(model);
		model.setSceneId(0);

	}

	public void recolorWallObject(WallObject wallObject)
	{
		Renderable renderable = wallObject.getRenderable1();
		Model model = verifyModel(renderable);
		Renderable renderable2 = null;
		Model model2 = null;
		// wallObjects can have 2 renderable models, not distinguishable and no good way to find them
		try
		{
			renderable2 = wallObject.getRenderable2();
			model2 = verifyModel(renderable2);
		}
		catch (NullPointerException ignore)
		{
		}
		if (model2 != null)
		{

			modelRecolorer.applyColorsDirectly(model2, 0);
			recordedModels.add(model2);
			sceneIDs.add(model2.getSceneId());
			model2.setSceneId(0);
		}
		if (model == null)
		{
			return;
		}
		modelRecolorer.applyColorsDirectly(model, 0);
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);
	}

	public void recolorNPC(NPC npc)
	{
		if (npc.getModel() == null)
		{
			log.debug("recolorNPC returned null! - NPC");
			return;
		}
		if (npc.getId() == LAVA_BEAST)
		{
			modelRecolorer.applyColors(npc.getId(), "NPC", npc.getModel(), config.lavaBeast() != GlobalColor.Default);
		}
		else
		{
			modelRecolorer.applyColors(npc.getId(), "NPC", npc.getModel(), config.boulder() != BoulderTypes.Default);
		}
		addToLists(npc.getModel());
		npc.getModel().setSceneId(0);
	}

	private void resetSceneIDs()
	{
		int size = sceneIDs.size();
		for (int i = 0; i < size; i++)
		{
			recordedModels.get(i).setSceneId(sceneIDs.get(i));
		}
		recordedModels.clear();
		sceneIDs.clear();
	}

	private Model verifyModel(Renderable renderable)
	{
		if (renderable instanceof Model)
		{
			return (Model) renderable;
		}
		else
		{
			try
			{
				Model model = renderable.getModel();
				if (model == null)
				{
					log.debug("verifyModel returned null!");
					return null;
				}
				return model;
			}
			catch (NullPointerException e)
			{
				return null;
			}
		}
	}

	private void recolorAllNPCs()
	{
		for (NPC npc : recordedNpcs)
		{
			recolorNPC(npc);
		}
	}

	public void clearAll()
	{
		for (GameObject g : recordedGameObjects)
		{
			Renderable renderable = g.getRenderable();
			Model model = verifyModel(renderable);
			if (model == null)
			{
				log.debug("clearAll returned null! - GameObject");
			}
			modelRecolorer.applyColors(g.getId(), "GameObject", model, false);
		}

		for (NPC n : recordedNpcs)
		{
			Renderable renderable = n.getModel();
			Model model = verifyModel(renderable);
			if (model == null)
			{
				log.debug("clearAll returned null! - NPC");
			}

			modelRecolorer.applyColors(n.getId(), "NPC", model, false);
		}

		for (GroundObject g : recordedGroundObjects)
		{
			Renderable renderable = g.getRenderable();
			Model model = verifyModel(renderable);
			if (model == null)
			{
				log.debug("clearAll returned null! - GroundObject");
			}

			modelRecolorer.applyColors(g.getId(), "GroundObject", model, false);
		}

		for (Projectile g : recordedProjectiles)
		{

			modelRecolorer.applyColors(g.getId(), "Projectile", g.getModel(), false);
		}

		for (GraphicsObject g : recordedGraphicsObjects)
		{

			modelRecolorer.applyColors(g.getId(), "GraphicsObject", g.getModel(), false);
		}

		resetSceneIDs();

		//freeing the stored data.
		recordedGameObjects.clear();
		recordedGroundObjects.clear();
		recordedNpcs.clear();
		recordedProjectiles.clear();
		recordedGraphicsObjects.clear();
	}

	// recolors all GameObjects, GroundObjects, NPCs (including Hunllef) and Projectiles to their desired colors, if they are stored in the corresponding list.
	// differentiating between NPCs and tornados, even though tornados are technically a NPC
	// Can't really use this one for VM because of the way walls are handled :(
	// public void recolorAll()

	private void addToLists(Model model)
	{
		if (!recordedModels.contains(model))
		{
			recordedModels.add(model);
		}

		if (!sceneIDs.contains(model.getSceneId()))
		{
			sceneIDs.add(model.getSceneId());
		}
	}

	private TileObject findTileObject(int z, int x, int y, int id)
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		final Tile tile = tiles[z][x][y];
		if (tile == null)
		{
			return null;
		}

		final GameObject[] tileGameObjects = tile.getGameObjects();

		for (GameObject object : tileGameObjects)
		{
			if (object.getId() == id)
			{
				return object;
			}
		}

		return null;
	}

	private int createColorMenu(int idx, String target, int id)
	{
		String name = "null";
		if (id == LAVA_BEAST)
		{
			name = "Lava Beast";
		}
		if (THE_BOULDER.contains(id) || THE_BOULDER_NPCS.contains(id))
		{
			name = "Boulder";
		}
		if (WALL_OBJECTS.contains(id))
		{
			name = "walls";
		}
		if (LOWER_LEVEL_FLOOR.contains(id) || UPPER_LEVEL_FLOOR.contains(id))
		{
			name = "Stuff";
		}
		List<Color> colors = List.of(config.boulderColor(), config.wallColor(), config.lavaColor(), config.platformColor(), config.lowerLevelFloorColor(), config.upperLevelFloorColor(), config.lavaBeastColor());
		//Dirty remove dupes
		colors = new ArrayList<>(new HashSet<>(colors));
		// add a few default colors
		for (Color default_ : new Color[]{Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA})
		{
			if (colors.size() < 5 && !colors.contains(default_))
			{
				colors.add(default_);
			}
		}

		MenuEntry parent = client.createMenuEntry(idx--)
			.setOption("Recolor")
			.setTarget(target)
			.setType(MenuAction.RUNELITE);
		Menu submenu = parent.createSubMenu();
		int yeet = 0;
		for (final Color c : colors)
		{
			submenu.createMenuEntry(yeet)
					.setOption(ColorUtil.prependColorTag("Set color", c))
					.setType(MenuAction.RUNELITE)
					.onClick(e -> updateConfig(id, c));
			yeet++;
		}
		submenu.createMenuEntry(yeet)
			.setOption("Pick color")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> SwingUtilities.invokeLater(() ->
			{
				RuneliteColorPicker colorPicker = colorPickerManager.create(SwingUtilities.windowForComponent((Applet) client),
					getObjectCurrentColor(id), "Recolor", true);
				colorPicker.setOnClose(c ->
					clientThread.invokeLater(() ->
						updateConfig(id, c)));
				colorPicker.setVisible(true);
			}));

		return idx;
	}

	private Color getObjectCurrentColor(int id)
	{
		if (WALL_OBJECTS.contains(id))
		{
			return config.wallColor();
		}
		else if (THE_BOULDER.contains(id) || THE_BOULDER_NPCS.contains(id))
		{
			return config.boulderColor();
		}
		else if (id == LAVA_BEAST)
		{
			return config.lavaBeastColor();
		}
		else
		{
			return Color.RED;
		}
	}

	private void updateConfig(int id, Color c)
	{
		if (WALL_OBJECTS.contains(id))
		{
			configManager.setConfiguration(CONFIG_GROUP, "wallCustomColor", c);
		}
		else if (THE_BOULDER.contains(id) || THE_BOULDER_NPCS.contains(id))
		{
			configManager.setConfiguration(CONFIG_GROUP, "BoulderCustomColor", c);
		}
		else if (id == LAVA_BEAST)
		{
			configManager.setConfiguration(CONFIG_GROUP, "lavaBeastCustomColor", c);
		}
	}

	@Provides
	VMRecolorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VMRecolorConfig.class);
	}
}
