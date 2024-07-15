package com.VMRecolor;

import com.VMRecolor.VMRecolorConfig.BoulderTypes;
import com.VMRecolor.VMRecolorConfig.GlobalColor;
import com.VMRecolor.VMRecolorConfig.LavaOptions;
import com.VMRecolor.VMRecolorConfig.PlatformOptions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.applet.Applet;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldArea;
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
@PluginDescriptor(name = "VMRecolor")
public class VMRecolorPlugin extends Plugin
{
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

	private ArrayList<GameObject> recordedGameObjects = new ArrayList<>();
	private ArrayList<GroundObject> recordedGroundObjects = new ArrayList<>();
	private ArrayList<WallObject> recordedWallObjects = new ArrayList<>();
	private ArrayList<Model> recordedModels = new ArrayList<>();
	private ArrayList<NPC> recordedNpcs = new ArrayList<>();
	private ArrayList<Projectile> recordedProjectiles = new ArrayList<>();
	private ArrayList<GraphicsObject> recordedGraphicsObjects = new ArrayList<>();
	private ArrayList<Integer> sceneIDs = new ArrayList<>();
	private Set<Integer> VMRegionIDs = ImmutableSet.of(15263, 15262, 15519, 15518, 15775, 15774);
	private WorldPoint SEWorldPoint = new WorldPoint(3830, 10119, 1);
	private WorldPoint otherWorldPoint = new WorldPoint(3798, 10192, 1);
	private WorldArea nonLavaWallArea = new WorldArea(SEWorldPoint, -45, 105);
	private WorldArea otherLavaWallArea = new WorldArea(otherWorldPoint, -3, 5);
	// Projectile 660 spark, 1403 Lava beast
	//31033 falling rock
	//1407 1406 graphics objects for vent launch and falling rock splat

	int regionId;

	public static final Set<Integer> THE_BOULDER = ImmutableSet.of(31034, 31035, 31036, 31037, 31038);
	public static final Set<Integer> THE_BOULDER_NPCS = ImmutableSet.of(7807, 7808, 7809, 7810, 7811, 7812, 7813, 7814, 7815, 7816);

	// All game objects with visible yellow lava
	// There is some overlap in certain places due to how Jagex chose to decorate the volcano
	public static final Set<Integer> LAVA = ImmutableSet.of(31001, 31002, 31039); // This is a ground object, not a game object
	private static final Set<Integer> GAME_OBJECTS = ImmutableSet.of(30998, 30999, 31000, 31002, 31003, 31004, 31005, 31006, 31007, 31008, 31009, 31010, 31011, 31012, 31013, 30996, 30995, 30994, 30993, 30992, 30991, 30990, 31039, 31014, 31015, 31016, 31017, 31018, 31019, 31020, 31021, 31022, 31023, 31024, 31025, 31026, 31027, 31028, 31029, 31030, 31042, 31043, 31044, 31045, 31046, 31047, 31048, 31049, 31050, 31051, 31052);
	public static final Set<Integer> LOWER_LEVEL_FLOOR = ImmutableSet.of(31003, 31004, 31005, 31006, 31007, 31008, 31009, 31010, 31011, 31012, 31013, 31026, 31028, 31030, 31033, 31042, 31043, 31044, 31045, 31046, 31047, 31048, 31049, 31050, 31051, 31052);
	public static final Set<Integer> UPPER_LEVEL_FLOOR = ImmutableSet.of(31014, 31015, 31016, 31017, 31018, 31019, 31020, 31021, 31022, 31023, 31024, 31025, 31027);
	public static final Set<Integer> PLATFORMS = ImmutableSet.of(30998, 30999, 31000);
	public static final Set<Integer> WALL_OBJECTS = ImmutableSet.of(30996, 30995, 30994, 30993, 30992, 30991, 30990);
	private static final Set<Integer> GRAPHICS_OBJECTS = ImmutableSet.of(1406, 1407);

	public static final Integer LAVA_BEAST = 7817;

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
				clientThread.invoke(() -> client.setGameState(GameState.LOADING));
			}
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientThread.invoke(() -> {
			clearAll();
			resetSceneIDs();

			//freeing the stored data.
			recordedGameObjects.clear();
			recordedGroundObjects.clear();
			recordedNpcs.clear();
			recordedModels.clear();
			recordedWallObjects.clear();
			recordedProjectiles.clear();
			recordedGraphicsObjects.clear();
			sceneIDs.clear();
			synchronized (modelRecolorer)
			{
				modelRecolorer.cleanUp();
			}
		});
		clientThread.invoke(() -> {
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.setGameState(GameState.LOADING);
			}
		});
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) || (event.getType() != MenuAction.EXAMINE_OBJECT.getId() && event.getMenuEntry().getType() != MenuAction.EXAMINE_NPC))
		{
			return;
		}
		NPC npc = null;
		if (event.getMenuEntry().getType() != MenuAction.EXAMINE_NPC)
		{
			npc = event.getMenuEntry().getNpc();
		}
		TileObject tileObject = null;
		if(event.getType() == MenuAction.EXAMINE_OBJECT.getId())
		{
			tileObject = findTileObject(client.getPlane(), event.getActionParam0(), event.getActionParam1(), event.getIdentifier());
			if (tileObject == null && npc == null)
			{
				return;
			}
		}


		int idx = -1;
		if (tileObject !=null && (THE_BOULDER.contains(tileObject.getId())))
			idx = createColorMenu(idx, event.getTarget(), tileObject.getId());
		if (npc != null && (npc.getId()==LAVA_BEAST))
			idx = createColorMenu(idx, event.getTarget(), npc.getId());

	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == 5937 && event.getValue() == 0) // Looks like this is the varb for when someone is in VM?
		{
			clearAll();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("VMRecolor"))
		{
			return;
		}

		if (event.getKey().equalsIgnoreCase("Boulder"))
		{
			recolorBoulder();
		}

		if (event.getKey().equalsIgnoreCase("LavaBeastRecolor"))
		{
			for (NPC npc : client.getNpcs())
			{
				if (npc.getId() == LAVA_BEAST)
				{
					recolorNPC(npc);
				}
			}
		}
		if (event.getKey().equalsIgnoreCase("HideLava"))
		{
			clientThread.invoke(this::clearAll);
		}
		// TODO: Implement a new function in ModelRecolorer to recalculate specific objects color at will to avoid recoloring EVERYTHING
		reload();
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
			{
				recolorGameObject(event.getGameObject());
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
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

		if (ID != 659 || !GRAPHICS_OBJECTS.contains(ID) || config.lava() == LavaOptions.Default)
		{
			return;
		}
		recordedGraphicsObjects.add(event.getGraphicsObject());
		recolorGraphicsObject(event.getGraphicsObject());
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
			recordedWallObjects.add(event.getWallObject());
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
		synchronized (modelRecolorer)
		{
			modelRecolorer.applyColors(graphicsObject.getId(), "GraphicsObject", model, config.lava() != LavaOptions.Default);
		}
	}

	public void recolorProjectile(Projectile projectile)
	{
		Model model = projectile.getModel();
		if (model == null)
		{
			log.debug("recolorProjectile returned null!");
			return;
		}
		synchronized (modelRecolorer)
		{
			modelRecolorer.applyColors(projectile.getId(), "Projectile", model, true);
		}
	}

	private void reload()
	{
		synchronized (modelRecolorer)
		{
			modelRecolorer.recolorData();
		}
		try
		{
			clientThread.invoke(() -> {
				clearAll();
				recolorAll();
			});
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				clientThread.invoke(() -> {
					client.setGameState(GameState.LOADING);
				});
			}
		}
		catch (NullPointerException ignored)
		{

		}
	}

	public void recolorBoulder()
	{
		List<NPC> npcs = client.getNpcs();
		for (NPC npc : npcs)
		{
			if (THE_BOULDER_NPCS.contains(npc.getId()))
			{
				recolorNPC(npc);
				break;
			}
		}
		Scene scene = client.getScene();
		for (Tile[][] tiles : scene.getTiles())
		{
			for (int x = 0; x < Constants.SCENE_SIZE; ++x)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; ++y)
				{
					Tile tile = tiles[x][y];
					if (tile == null)
					{
						continue;
					}
					for (GameObject gameObject : tile.getGameObjects())
					{
						if (gameObject != null && (THE_BOULDER.contains(gameObject.getId())))
						{
							recolorGameObject(gameObject);
							return;
						}
					}
				}
			}
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
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(gameObject.getId(), "GameObject", model, config.boulder() != BoulderTypes.Default);
			}
		}
		else if (gameObject.getId() <= 30992 && gameObject.getId() >= 30990)
		{
			recolor30992(gameObject, model);
		}
		else
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(gameObject.getId(), "GameObject", model, true);
			}
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

		synchronized (modelRecolorer)
		{
			modelRecolorer.applyColors(groundObject.getId(), "GroundObject", model, true);
		}
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);

	}

	public void recolorWallObject(WallObject wallObject)
	{
		Renderable renderable = wallObject.getRenderable1();
		Model model = verifyModel(renderable);
		Renderable renderable2 = null;
		Model model2 = null;
		try
		{
			renderable2 = wallObject.getRenderable2();
			model2 = verifyModel(renderable2);
		}
		catch (NullPointerException e)
		{
		}
		Color colorToApply = config.wallColor();
		if (model2 != null)
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyWallColors(model2, colorToApply, isLavaObj(wallObject.getWorldLocation()));
			}
			recordedModels.add(model2);
			sceneIDs.add(model2.getSceneId());
			model2.setSceneId(0);
		}
		if (model == null)
		{
			return;
		}
		synchronized (modelRecolorer)
		{
			modelRecolorer.applyWallColors(model, colorToApply, isLavaObj(wallObject.getWorldLocation()));
		}
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);
	}

	// These particular models are used interchangeably with lava and non-lava walls
	// creating a uniquely annoying graphical bug
	private void recolor30992(GameObject gameObject, Model model)
	{
		if (isLavaObj(gameObject.getWorldLocation()))
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyWallColors(model, config.wallColor(), true);
			}
		}
		else
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyWallColors(model, config.wallColor(), false);
			}
		}
	}

	private boolean isLavaObj(WorldPoint worldPoint)
	{

		// It needs to not be the upper floor plane
		if (worldPoint.getPlane() == 3)
		{
			return false;
		}
		// It needs to not be decorative in some weird unreachable place
		if (worldPoint.getX() > 3900 && worldPoint.getPlane() > 1)
		{
			return false;
		}
		// It needs to not be the single wall object in the north part of the volcano
		// Have to check this one because it is an intersection with nonLavaWallArea
		if (otherLavaWallArea.contains(worldPoint))
		{
			return true;
		}
		// It needs to be the outside playable boundary
		if (nonLavaWallArea.contains(worldPoint))
		{
			return false;
		}
		return true;
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
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(npc.getId(), "NPC", npc.getModel(), config.lavaBeast() != GlobalColor.Default);
			}
		}
		else
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(npc.getId(), "NPC", npc.getModel(), config.boulder() != BoulderTypes.Default);
			}
		}
		recordedModels.add(npc.getModel());
		sceneIDs.add(npc.getModel().getSceneId());
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
			catch(NullPointerException e)
			{
				return null;
			}
		}
	}

	private void printFaceColors(Model model, int objectId)
	{
		int[] faceColors1, faceColors2, faceColors3;
		try
		{
			faceColors1 = model.getFaceColors1();
			faceColors2 = model.getFaceColors2();
			faceColors3 = model.getFaceColors3();
		}
		catch (NullPointerException e)
		{
			log.info("Facecolors for model {} are null?", objectId);
			return;
		}
		System.out.print("FaceColors1: [");
		for (int i = 0; i < faceColors1.length; i++)
		{
			System.out.print(faceColors1[i]);
			if (i != faceColors1.length - 1)
			{
				System.out.print(",");
			}
			else
			{
				System.out.print("]");
			}
		}
		System.out.println();

		System.out.print("FaceColors2: [");
		for (int i = 0; i < faceColors2.length; i++)
		{
			System.out.print(faceColors2[i]);
			if (i != faceColors2.length - 1)
			{
				System.out.print(",");
			}
			else
			{
				System.out.print("]");
			}
		}
		System.out.println();

		System.out.print("FaceColors3: [");
		for (int i = 0; i < faceColors3.length; i++)
		{
			System.out.print(faceColors3[i]);
			if (i != faceColors3.length - 1)
			{
				System.out.print(",");
			}
			else
			{
				System.out.print("]");
			}
		}
		System.out.println("\n");
	}

	public void clearAll()
	{
		for (int i = 0; i < recordedGameObjects.size(); i++)
		{
			GameObject g = recordedGameObjects.get(i);

			Renderable renderable = g.getRenderable();
			Model model = verifyModel(renderable);
			if (model == null)
			{
				log.debug("clearAll returned null! - GameObject");
			}
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(g.getId(), "GameObject", model, false);
			}
		}

		for (int i = 0; i < recordedNpcs.size(); i++)
		{
			NPC n = recordedNpcs.get(i);

			Renderable renderable = n.getModel();
			Model model = verifyModel(renderable);
			if (model == null)
			{
				log.debug("clearAll returned null! - GameObject");
			}
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(n.getId(), "NPC", model, false);
			}
		}

		for (int i = 0; i < recordedGroundObjects.size(); i++)
		{
			GroundObject g = recordedGroundObjects.get(i);

			Renderable renderable = g.getRenderable();
			Model model = verifyModel(renderable);
			if (model == null)
			{
				log.debug("clearAll returned null! - GroundObject");
			}
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(g.getId(), "GroundObject", model, false);
			}
		}

		for (int i = 0; i < recordedProjectiles.size(); i++)
		{
			Projectile g = recordedProjectiles.get(i);
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(g.getId(), "Projectile", g.getModel(), false);
			}
		}

		for (int i = 0; i < recordedGraphicsObjects.size(); i++)
		{
			GraphicsObject g = recordedGraphicsObjects.get(i);
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(g.getId(), "GraphicsObject", g.getModel(), false);
			}
		}
	}

	// recolors all GameObjects, GroundObjects, NPCs (including Hunllef) and Projectiles to their desired colors, if they are stored in the corresponding list.
	// differentiating between NPCs and tornados, even though tornados are technically a NPC
	public void recolorAll()
	{
		for (GameObject gameObject : recordedGameObjects)
		{
			recolorGameObject(gameObject);
		}

		for (GroundObject groundObject : recordedGroundObjects)
		{
			recolorGroundObject(groundObject);
		}

		for (WallObject wallObject : recordedWallObjects)
		{
			recolorWallObject(wallObject);
		}
		for (NPC npc : recordedNpcs)
		{
			recolorNPC(npc);
		}
		for (Projectile projectile : recordedProjectiles)
		{
			recolorProjectile(projectile);
		}
		for (GraphicsObject graphicsObject : recordedGraphicsObjects)
		{
			recolorGraphicsObject(graphicsObject);
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
			name = "Lava Beast";
		if (THE_BOULDER.contains(id) || THE_BOULDER_NPCS.contains(id))
			name = "Boulder";
		if (WALL_OBJECTS.contains(id))
			name = "walls";
		List<Color> colors = List.of(config.boulderColor(), config.wallColor(), config.lavaColor(), config.platformColor(), config.lowerLevelFloorColor(), config.upperLevelFloorColor(), config.lavaBeastColor());
		// add a few default colors
		for (Color default_ : new Color[]{Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA})
		{
			if (colors.size() < 5 && !colors.contains(default_))
			{
				colors.add(default_);
			}
		}

		MenuEntry parent = client.createMenuEntry(idx--)
			.setOption("Recolor "+name)
			.setTarget(target)
			.setType(MenuAction.RUNELITE_SUBMENU);

		for (final Color c : colors)
		{
			client.createMenuEntry(idx--)
				.setOption(ColorUtil.prependColorTag("Set color", c))
				.setType(MenuAction.RUNELITE)
				.setParent(parent)
				.onClick(e -> updateConfig(id, c));
		}

		client.createMenuEntry(idx--)
			.setOption("Pick color")
			.setType(MenuAction.RUNELITE)
			.setParent(parent)
			.onClick(e -> SwingUtilities.invokeLater(() ->
			{
				RuneliteColorPicker colorPicker = colorPickerManager.create(SwingUtilities.windowForComponent((Applet) client),
					getObjectCurrentColor(id), "Recolor", false);
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
			configManager.setConfiguration("VMRecolor","wallCustomColor",c);
		}
		else if (THE_BOULDER.contains(id) || THE_BOULDER_NPCS.contains(id))
		{
			configManager.setConfiguration("VMRecolor","BoulderCustomColor",c);
		}
		else if (id == LAVA_BEAST)
		{
			configManager.setConfiguration("VMRecolor","lavaBeastCustomColor",c);
		}
	}

	@Provides
	VMRecolorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VMRecolorConfig.class);
	}
}
