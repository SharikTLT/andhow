package yarnandtail.andhow;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import yarnandtail.andhow.load.LoaderState;
import yarnandtail.andhow.name.BasicNamingStrategy;

/**
 *
 * @author eeverman
 */
public class AppConfig {
	
	private static AppConfig singleInstance;
	private static Object lock = new Object();
	
	//User config
	private final Map<ConfigPoint<?>, Object> forcedValues = new HashMap();
	private final List<Loader> loaders = new ArrayList();
	private NamingStrategy naming = new BasicNamingStrategy();
	private final List<String> cmdLineArgs = new ArrayList();
	
	//Internal state
	
	//Note: This should be an AtomicReference to ensure we don't transiently 
	//assign to null when updating.
	private AppConfigDefinition appConfigDef;
	private final List<Map<ConfigPoint<?>, Object>> loadedValues = new ArrayList();
	
	private AppConfig(NamingStrategy naming, List<Loader> loaders, List<Class<? extends ConfigPointGroup>> registeredGroups, String[] cmdLineArgs, HashMap<ConfigPoint<?>, Object> startingValues) {
		doReset(this, naming, loaders, registeredGroups, cmdLineArgs, startingValues);

	}
	
	public static AppConfig instance() {
		if (singleInstance != null) {
			return singleInstance;
		} else {
			synchronized (lock) {
				if (singleInstance != null) {
					return singleInstance;
				} else {
					singleInstance = new AppConfig(new BasicNamingStrategy(), null, null, null, null);
					return singleInstance;
				}
			}
		}
	}
	
	public static AppConfig instance(
			NamingStrategy naming, List<Loader> loaders, List<Class<? extends ConfigPointGroup>> registeredGroups, 
			String[] cmdLineArgs, HashMap<ConfigPoint<?>, Object> startingValues) throws ConfigurationException {
		
		if (singleInstance != null) {
			throw new RuntimeException("Already constructed!");
		} else {
			synchronized (lock) {
				if (singleInstance != null) {
					throw new RuntimeException("Already constructed!");
				} else {
					singleInstance = new AppConfig(naming, loaders, registeredGroups, cmdLineArgs, startingValues);
					return singleInstance;
				}
			}
		}
	}
	
	public List<Class<? extends ConfigPointGroup>> getGroups() {
		return appConfigDef.getGroups();
	}

	public List<ConfigPoint<?>> getPoints() {
		return appConfigDef.getPoints();
	}
	
	public boolean isPointPresent(ConfigPoint<?> point) {
		return getValue(point) != null;
	}
	
	public Object getValue(ConfigPoint<?> point) {
		for (Map<ConfigPoint<?>, Object> map : loadedValues) {
			if (map.containsKey(point)) {
				return map.get(point);
			}
		}
		
		return null;
	}
	
	private static void doReset(AppConfig instanceToReset, NamingStrategy naming, List<Loader> loaders, 
			List<Class<? extends ConfigPointGroup>> registeredGroups, String[] cmdLineArgs, 
			HashMap<ConfigPoint<?>, Object> forcedValues) throws ConfigurationException {
		doReset(instanceToReset, naming, loaders, registeredGroups, cmdLineArgs, 
			forcedValues, System.err);
	}
	
	private static void doReset(AppConfig instanceToReset, NamingStrategy naming, List<Loader> loaders, 
			List<Class<? extends ConfigPointGroup>> registeredGroups, String[] cmdLineArgs, 
			HashMap<ConfigPoint<?>, Object> forcedValues, PrintStream errorStream) throws ConfigurationException {
		
		synchronized (lock) {
			instanceToReset.naming = naming;
			instanceToReset.loaders.clear();
			instanceToReset.forcedValues.clear();
			instanceToReset.appConfigDef = null;	//TODO:  how should this work
			instanceToReset.cmdLineArgs.clear();
			instanceToReset.loadedValues.clear();

			if (loaders != null) {
				instanceToReset.loaders.addAll(loaders);
			}
			if (forcedValues != null) {
				instanceToReset.forcedValues.putAll(forcedValues);
			}

			if (cmdLineArgs != null && cmdLineArgs.length > 0) {
				instanceToReset.cmdLineArgs.addAll(Arrays.asList(cmdLineArgs));
			}
			
			instanceToReset.appConfigDef = AppConfigUtil.doRegisterConfigPoints(registeredGroups, instanceToReset.naming);
			
			List<NamingException> nameExceptions = instanceToReset.appConfigDef.getNamingExceptions();
			if (nameExceptions.size() > 0) {
				AppConfigUtil.printNamingExceptions(nameExceptions, errorStream);
				
				if (nameExceptions.size() == 1) {
					throw new ConfigurationException(
							"Unable to continue w/ configuration loading because "
							+ "there is a single naming error.  "
							+ "See the 'Caused by' section for the error.  "
							+ "See System.err for more detail on the actual "
							+ "params causing the error.",
							nameExceptions.get(0));
				} else {
					throw new ConfigurationException(
							"Unable to continue w/ configuration loading because "
							+ "there are multiple naming errors.  "
							+ "See the 'Caused by' section for first of those errors.  "
							+ "See System.err for more detail on the actual "
							+ "params causing the error.",
							nameExceptions.get(0));
				}
			}
			
			instanceToReset.doLoad();
			
		}
	}
	
	private void doLoad() {
		
		synchronized (lock) {
			List<Map<ConfigPoint<?>, Object>> existingValues = new ArrayList();

			if (forcedValues.size() > 0) {
				existingValues.add(forcedValues);
			}

			LoaderState state = new LoaderState(cmdLineArgs, existingValues, appConfigDef);
			for (Loader loader : loaders) {
				Map<ConfigPoint<?>, Object> result = loader.load(state);
				if (result.size() > 0) existingValues.add(result);
			}

			loadedValues.clear();
			loadedValues.addAll(existingValues);
		}
	}
	
	/**
	 * Mostly for testing - a backdoor to reset
	 * @param startingValues 
	 */
	public static void reset(NamingStrategy naming, List<Loader> loaders, 
			List<Class<? extends ConfigPointGroup>> registeredGroups, String[] cmdLineArgs, 
			HashMap<ConfigPoint<?>, Object> forcedValues) {
		synchronized (lock) {
			
			if (singleInstance == null) {
				singleInstance = new AppConfig(naming, loaders, registeredGroups, cmdLineArgs, forcedValues);
			} else {
				doReset(singleInstance, naming, loaders, registeredGroups, cmdLineArgs, forcedValues);
			}
		}
	}

}
