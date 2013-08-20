package org.ssast.minecraft.version;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.ssast.minecraft.Config;
import org.ssast.minecraft.download.DownloadCallbackAdapter;
import org.ssast.minecraft.download.Downloadable;
import org.ssast.minecraft.download.Downloader;
import org.ssast.minecraft.util.EasyFileAccess;
import org.ssast.minecraft.util.EasyZipAccess;
import org.ssast.minecraft.util.Lang;

public class RunnableModule extends Module {

	protected Version version = null;

	private int installState = -1;
	
	private boolean isUninstalling = false;
	
	private RunnableModuleInfo moduleInfo = null;
	
	public RunnableModule(ModuleInstallCallback icallback,	ModuleUninstallCallback ucallback) {
		super(icallback, ucallback);
	}

	public void install() {
		if(isUninstalling) {
			System.out.println(Lang.getString("msg.module.isuninstalling"));
			return;
		}
		if(isDownloading()) {
			System.out.println(Lang.getString("msg.module.isinstalling"));
			return;
		}

		if(moduleInfo != null && !moduleInfo.canRunInThisOS()) {
			System.out.println(Lang.getString("msg.module.notallowed"));
			System.out.println(Lang.getString("msg.module.reason") + moduleInfo.incompatibilityReason);
			System.out.println(Lang.getString("msg.module.failed") + "[" + getName() + "]");
			return;
		}

		moduleDownloader = new Downloader();

		System.out.println(Lang.getString("msg.module.start") + "[" + getName() + "]");

		if(!tryLoadModuleInfo()) {
			moduleDownloader.addDownload(
				new Downloadable(getModuleJsonUrl(),
				new GameDownloadCallback("json", null)));
		} else {
			addDownloadList();
		}
		
		moduleDownloader.stopAfterAllDone();
		moduleDownloader.start();
	}
	
	class GameDownloadCallback extends DownloadCallbackAdapter {
		private String type;
		private Library lib;

		public GameDownloadCallback(String type, Library lib) {
			this.type = type;
			this.lib = lib;
		}

		@Override
		public void downloadDone(Downloadable d, boolean succeed, boolean queueEmpty) {

			if(succeed) {
				if(type.equals("json")) {
					JSONObject json = new JSONObject(d.getDownloaded());
					moduleInfo = new RunnableModuleInfo(json);

					if(!moduleInfo.canRunInThisOS()) {
						System.out.println(Lang.getString("msg.module.notallowed"));
						System.out.println(Lang.getString("msg.module.reason") + moduleInfo.incompatibilityReason);
						moduleDownloader.forceStop();
						System.out.println(Lang.getString("msg.module.failed") + "[" + getName() + "]");
						return;
					}

					new File(getModuleJsonPath()).getParentFile().mkdirs();
					EasyFileAccess.saveFile(getModuleJsonPath(), json.toString(2));
					
					addDownloadList();

				} else if(type.equals("bin")) {
					File file = new File(d.getSavedFile());
					File fileReal;
					
					if(lib != null) {
						fileReal = new File(lib.getRealFilePath());
					} else {
						fileReal = new File(getModuleJarPath());
					}
					
					fileReal.delete();
					file.renameTo(fileReal);
					
					if(lib != null && lib.needExtract()) {
						System.out.println(Lang.getString("msg.zip.unzip") + lib.getKey());
						
						List<String> excludes = lib.getExtractExclude();
						String extractBase = lib.getNativeExtractedPath() + "/";
						new File(extractBase).mkdirs();

						EasyZipAccess.extractZip(fileReal.getPath(), 
							lib.getExtractTempPath() + "/", extractBase, excludes, "");
					}

					if(queueEmpty) {
						finishInstall();
					}
				}
			} else {
				moduleDownloader.forceStop();
				System.out.println(Lang.getString("msg.module.failed") + "[" + getName() + "]");
			}
		}
	}

	private void addDownloadList() {

		installCallback.installStart();

		int addCount = 0;

		if(!new File(getModuleJarPath()).isFile()) {

			new File(getModuleJarTempPath()).getParentFile().mkdirs();
			new File(getModuleJarPath()).getParentFile().mkdirs();
			moduleDownloader.addDownload(
				new Downloadable(getModuleJarUrl(), getModuleJarTempPath(),
				new GameDownloadCallback("bin", null)));

			addCount++;
		}

		for(Library lib : moduleInfo.libraries) {
			if(!lib.needDownloadInOS())
				continue;

			if(new File(lib.getRealFilePath()).isFile())
				continue;

			new File(lib.getTempFilePath()).getParentFile().mkdirs();
			new File(lib.getRealFilePath()).getParentFile().mkdirs();

			moduleDownloader.addDownload(
					new Downloadable(lib.getFullUrl(), lib.getTempFilePath(),
					new GameDownloadCallback("bin", lib)));
			
			addCount++;
		}
		
		if(addCount == 0) {
			new Thread() {
				@Override
				public void run() {
					finishInstall();
				}
			}.start();
		}
	}
	
	private void finishInstall() {

		if(!new File(getModuleJarRunPath()).isFile()) {
			EasyZipAccess.extractZip(getModuleJarPath(), 
				getModuleJarExtractTempPath(), 
				getModuleJarExtractPath(), Arrays.asList("META-INF/"), "run");
			EasyZipAccess.generateJar(getModuleJarRunPath(),
				getModuleJarExtractPath(), "run");
		}

		System.out.println(Lang.getString("msg.module.succeeded") + "[" + getName() + "]");
		installState = 1;
		if(installCallback != null)
			installCallback.installDone();
	}

	public void uninstall() {
		if(isUninstalling) {
			System.out.println(Lang.getString("msg.module.isuninstalling"));
			return;
		}
		if(isDownloading()) {
			System.out.println(Lang.getString("msg.module.isinstalling"));
			return;
		}
		if(!tryLoadModuleInfo()) {
			System.out.println(Lang.getString("msg.module.notinstalled"));
			return;
		}

		System.out.println(Lang.getString("msg.module.startuninstall") + "[" + getName() + "]");
		
		isUninstalling = true;
		installState = 0;
		uninstallCallback.uninstallStart();

		new Thread() {
			@Override
			public void run() {
				
				File versionDir = new File(getModuleJsonPath()).getParentFile();
				System.out.println(Lang.getString("msg.module.delete") + versionDir.getPath());
				EasyFileAccess.deleteFileForce(versionDir);
				
				List<Library> toRemove = new ArrayList<Library>();
				if(tryLoadModuleInfo()) {
					toRemove.addAll(moduleInfo.libraries);
				}
		
				for(Module m : ModuleManager.modules) {
					if(!(m instanceof RunnableModule))
						continue;
					if(!m.isInstalled())
						continue;
					if(m == RunnableModule.this)
						continue;
					toRemove.removeAll(((RunnableModule)m).moduleInfo.libraries);
				}
				
				for(Library l : toRemove) {
					if(!l.needDownloadInOS())
						continue;
					
					File libFile = new File(l.getRealFilePath());
					System.out.println(Lang.getString("msg.module.delete") + libFile.getPath());
					libFile.delete();
					
					if(l.needExtract()) {
						File libExtract = new File(l.getNativeExtractedPath());
						System.out.println(Lang.getString("msg.module.delete") + libExtract.getPath());
						EasyFileAccess.deleteFileForce(libExtract);
					}
					
					do {
						libFile = libFile.getParentFile();
					} while(!libFile.getName().equals("libraries") && libFile.delete());
				}

				isUninstalling = false;
				System.out.println(Lang.getString("msg.module.uninstallsucceeded") + "[" + RunnableModule.this.getName() + "]");
				moduleInfo = null;
				uninstallCallback.uninstallDone();
			}
		}.start();
	}

	public String getName() {
		return version.id;
	}

	public boolean isInstalled() {

		if(installState == -1) {

			if(!new File(getModuleJarPath()).isFile() || 
					!new File(getModuleJarRunPath()).isFile()) {
				installState = 0;
				return false;
			}

			if(!tryLoadModuleInfo()) {
				installState = 0;
				return false;
			}

			try {
				
				for(Library lib : moduleInfo.libraries) {
					if(!lib.needDownloadInOS())
						continue;
		
					String path = lib.getRealFilePath();

					if(!new File(path).isFile()) {
						installState = 0;
						return false;
					}
				}
				
			} catch(Exception e) {
				installState = 0;
				return false;
			}

			installState = 1;
		}
		
		return installState == 1;
	}
	
	public String[] getRunningParams() {
		return moduleInfo.minecraftArguments;
	}

	public String getMainClass() {
		return moduleInfo.mainClass;
	}
	
	public String getClassPath() {
		StringBuilder sb = new StringBuilder();
		String separator = System.getProperty("path.separator");
		
		for(int i=0; i<moduleInfo.libraries.size(); i++) {
			Library lib = moduleInfo.libraries.get(i);
			if(lib.needExtract())
				continue;
			if(!lib.needDownloadInOS())
				continue;
			sb.append(lib.getRealFilePath().replace('/', System.getProperty("file.separator").charAt(0)));
			sb.append(separator);
		}

		sb.append(getModuleJarRunPath().replace('/', System.getProperty("file.separator").charAt(0)));
		sb.append(separator);

		if(sb.length() > 0)
			sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}
	
	public String getNativePath() {
		StringBuilder sb = new StringBuilder();
		String separator = System.getProperty("path.separator");
		
		for(int i=0; i<moduleInfo.libraries.size(); i++) {
			Library lib = moduleInfo.libraries.get(i);
			if(!lib.needExtract())
				continue;
			sb.append(lib.getNativeExtractedPath().replace('/', System.getProperty("file.separator").charAt(0)));
			sb.append(separator);
		}

		if(sb.length() > 0)
			sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	public String getReleaseTime() {
		if(version.releaseTime != null)
			return version.releaseTime;
		if(tryLoadModuleInfo())
			return moduleInfo.releaseTime;
		return " ";
	}

	public String getType() {
		if(version.type != null)
			return version.type;
		if(tryLoadModuleInfo())
			return moduleInfo.type;
		return "unknown";
	}

	public String getState() {
		if(isInstalled()) {
			return Lang.getString("ui.module.installed");
		}
		if(tryLoadModuleInfo()) {
			return Lang.getString("ui.module.notfinished");
		}
		return Lang.getString("ui.module.notinstalled");
	}
	
	private String getModuleJsonUrl() {
		return Config.MINECRAFT_DOWNLOAD_BASE + String.format(Config.MINECRAFT_VERSION_FORMAT, getName(), getName());
	}

	private String getModuleJsonPath() {
		return Config.gamePath + String.format(Config.MINECRAFT_VERSION_FORMAT, getName(), getName());
	}
	
	private String getModuleJarUrl() {
		return Config.MINECRAFT_DOWNLOAD_BASE + String.format(Config.MINECRAFT_VERSION_GAME_FORMAT, getName(), getName());
	}
	
	private String getModuleJarPath() {
		return Config.gamePath + String.format(Config.MINECRAFT_VERSION_GAME_FORMAT, getName(), getName());
	}

	private String getModuleJarTempPath() {
		return Config.TEMP_DIR + String.format(Config.MINECRAFT_VERSION_GAME_FORMAT, getName(), getName());
	}
	
	private String getModuleJarRunPath() {
		return Config.gamePath + String.format(Config.MINECRAFT_VERSION_GAME_RUN_FORMAT, getName(), getName());
	}

	private String getModuleJarExtractPath() {
		return Config.TEMP_DIR + String.format(Config.MINECRAFT_VERSION_GAME_EXTRACT_FORMAT, getName(), getName());
	}
	
	private String getModuleJarExtractTempPath() {
		return Config.TEMP_DIR + String.format(Config.MINECRAFT_VERSION_GAME_EXTRACT_TEMP_FORMAT, getName(), getName());
	}
	
	private boolean tryLoadModuleInfo() {
		if(moduleInfo != null)
			return true;
		
		String resourceStr = EasyFileAccess.loadFile(getModuleJsonPath());
		if(resourceStr == null) {
			return false;
		}

		try {
			moduleInfo = new RunnableModuleInfo(new JSONObject(resourceStr));
		} catch(Exception e) {
			return false;
		}
		return true;
	}
}
