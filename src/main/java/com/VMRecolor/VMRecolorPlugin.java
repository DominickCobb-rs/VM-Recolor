package com.VMRecolor;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(name = "VMRecolor")
public class VMRecolorPlugin extends Plugin
{

	// Can remove tiles from api.Scene
	// Scene.GetTiles()
	@Inject
	private Client client;

	@Inject
	private VMRecolorConfig config;

	@Inject
	private ClientThread clientThread;

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

	ModelRecolorer modelRecolorer;
	int regionId;

	public static final Set<Integer> THE_BOULDER = ImmutableSet.of(31034, 31035, 31036, 31037, 31038);
	public static final Set<Integer> THE_BOULDER_NPCS = ImmutableSet.of(7807, 7808, 7809, 7810, 7811, 7812, 7813, 7814, 7815, 7816);

	// All game objects with visible yellow lava
	// There is some overlap in certain places due to how Jagex chose to decorate the volcano
	public static final int PATH_LAVA = 31001; // This is a ground object, not a game object
	public static final Set<Integer> LAVA_OBJECTS = ImmutableSet.of(30990, 30991, 30993, 30994, 30995, 30996, 31002, 31003, 31004, 31005, 31006, 31007, 31008, 31009, 31010, 31011, 31012, 31013, 31039);

	private static final Set<Integer> GAME_OBJECTS = ImmutableSet.of(30998, 30999, 31000, 31002, 31003, 31004, 31005, 31006, 31007, 31008, 31009, 31010, 31011, 31012, 31013, 30996, 30995, 30994, 30993, 30992, 30991, 30990, 31039, 31014, 31015, 31016, 31017, 31018, 31019, 31020, 31021, 31022, 31023, 31024, 31025, 31026, 31027, 31028, 31029, 31030, 31042, 31043, 31044, 31045, 31046, 31047, 31048, 31049, 31050, 31051, 31052);

	private static final Set<Integer> WALL_OBJECTS = ImmutableSet.of(30996, 30995, 30994, 30993, 30992, 30991, 30990);
	private static final Set<Integer> GRAPHICS_OBJECTS = ImmutableSet.of(1406, 1407);

	public static final Integer LAVA_BEAST = 7817;

	@Override
	protected void startUp() throws Exception
	{
		if (config.customColor())
		{
			this.modelRecolorer = new ModelRecolorer("/model_facecolors.txt", config.color(), config.brightness(), config.onlyLava(), config.hideLava(), config.whiteBrightness());
		}
		else
		{
			this.modelRecolorer = new ModelRecolorer("/model_facecolors.txt", null, config.brightness(), config.onlyLava(), config.hideLava(), config.whiteBrightness());
		}
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
				modelRecolorer = null;
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
	public void onCommandExecuted(CommandExecuted command)
	{
		if (command.getCommand().equalsIgnoreCase("vm"))
		{
			modelRecolorer.hideThisColor = Integer.parseInt(command.getArguments()[0]);
			if (config.customColor())
			{
				reload(config.color());
			}
			else
			{
				reload(null);
			}
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
			return;
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
			clientThread.invoke(() -> clearAll());
		}

		if (config.customColor())
		{
			reload(config.color());
		}
		else
		{
			reload(null);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (!inVMRegion())
		{
			return;
		}

		if (GAME_OBJECTS.contains(event.getGameObject().getId()) || THE_BOULDER.contains(event.getGameObject().getId()))
		{
			int ID = event.getGameObject().getId();
			recordedGameObjects.add(event.getGameObject());
			if (!config.hideLava() && !config.customColor() && config.brightness() == 1.0)
			{
				return;
			}

			if (
				(GAME_OBJECTS.contains(ID) && config.customColor())
					|| (config.boulder() && config.customColor() && THE_BOULDER.contains(ID))
					|| (LAVA_OBJECTS.contains(ID) && !config.customColor())
					|| (LAVA_OBJECTS.contains(ID) && config.onlyLava() && config.customColor())
				|| (LAVA_OBJECTS.contains(ID) && config.hideLava())
			)
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
			if (!config.customColor() || !config.lavaBeast())
			{
				return;
			}
			recolorNPC(event.getNpc());
		}
		if (THE_BOULDER_NPCS.contains(event.getNpc().getId()) && config.customColor() && config.boulder())
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
			if (!config.customColor() || !config.lavaBeast())
			{
				return;
			}
			recolorNPC(event.getNpc());
		}
		if (THE_BOULDER_NPCS.contains(event.getNpc().getId()) && config.customColor() && config.boulder())
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
		if (!config.hideLava() && !config.customColor() && config.brightness() == 1.0)
		{
			return;
		}
		int ID = event.getGroundObject().getId();
		if (PATH_LAVA == ID)
		{
			recordedGroundObjects.add(event.getGroundObject());
			recolorGroundObject(event.getGroundObject());
		}
		if (ID == 659)
		{
			Renderable renderable = event.getGroundObject().getRenderable();
			Model model = verifyModel(renderable);
			printFaceColors(model, ID);
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (!inVMRegion())
		{
			return;
		}
		if (!config.customColor())
		{
			return;
		}
		int ID = event.getGraphicsObject().getId();

		if (ID != 659 || !GRAPHICS_OBJECTS.contains(ID))
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
		if (!config.customColor() && config.brightness() == 1.0 && !config.hideLava())
		{
			return;
		}
		if (WALL_OBJECTS.contains(event.getWallObject().getId()))
		{
			recordedWallObjects.add(event.getWallObject());
			if (!config.customColor() && config.brightness() == 1.0)
			{
				return;
			}
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
		if (config.customColor() && !config.onlyLava())
		{

			if (projectileMoved.getProjectile().getId() == 660)
			{
				recordedProjectiles.add(projectileMoved.getProjectile());
				recolorProjectile(projectileMoved.getProjectile());
				return;
			}
			if (projectileMoved.getProjectile().getId() == 1403 && config.lavaBeast())
			{
				recordedProjectiles.add(projectileMoved.getProjectile());
				recolorProjectile(projectileMoved.getProjectile());
			}
		}
	}

	private boolean inVMRegion()
	{
		regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
		return VMRegionIDs.contains(regionId);
	}

	public void recolorGraphicsObject(GraphicsObject graphicsObject)
	{
		if (config.customColor() && !config.onlyLava())
		{
			Model model = graphicsObject.getModel();
			if (model == null)
			{
				log.debug("recolorProjectile returned null!");
				return;
			}
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(graphicsObject.getId(), "GraphicsObject", model, config.customColor());
			}
		}
	}

	public void recolorProjectile(Projectile projectile)
	{
		if (config.customColor() && !config.onlyLava())
		{
			Model model = projectile.getModel();
			if (model == null)
			{
				log.debug("recolorProjectile returned null!");
				return;
			}
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(projectile.getId(), "Projectile", model, config.customColor());
			}
		}
	}

	private void reload(Color color)
	{
		synchronized (modelRecolorer)
		{
			modelRecolorer.update(color, config.brightness(), config.onlyLava(), config.hideLava(), config.whiteBrightness());
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
		catch (NullPointerException e)
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
		if (!LAVA_OBJECTS.contains(gameObject.getId()) && config.onlyLava())
		{
			return;
		}
		if (THE_BOULDER.contains(gameObject.getId()))
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(gameObject.getId(), "GameObject", model, (config.customColor() && config.boulder())); // config.color(), was between gameobject and mode for applybouldercolors
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
		Color colorToApply;
		if (config.customColor())
		{
			colorToApply = config.color();
		}
		else
		{
			colorToApply = null;
		}
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
				modelRecolorer.applyWallColors(model, config.customColor() ? config.color() : null, true);
			}
		}
		else
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyWallColors(model, config.customColor() ? config.color() : null, false);
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
				modelRecolorer.applyColors(npc.getId(), "NPC", npc.getModel(), config.lavaBeast());
			}
		}
		else
		{
			synchronized (modelRecolorer)
			{
				modelRecolorer.applyColors(npc.getId(), "NPC", npc.getModel(), (config.customColor() && config.boulder()));
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
			Model model = renderable.getModel();
			if (model == null)
			{
				log.debug("verifyModel returned null!");
				return null;
			}
			return model;
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

	@Provides
	VMRecolorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VMRecolorConfig.class);
	}
}
