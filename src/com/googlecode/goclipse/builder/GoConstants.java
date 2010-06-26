package com.googlecode.goclipse.builder;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.googlecode.goclipse.Activator;
import com.googlecode.goclipse.preferences.GoPreferencePage;
import com.googlecode.goclipse.preferences.PreferenceConstants;

public class GoConstants {
	public static final String GO_SOURCE_FILE_EXTENSION = ".go";

	public static final String OBJ_FILE_DIRECTORY       = "obj";
	public static final String EXE_FILE_DIRECTORY       = "out";
	public static final String COMPILER_OPTION_I = "-I";
	public static final String COMPILER_OPTION_L = "-L";
	public static final String COMPILER_OPTION_O = "-o";
	public static final String COMPILER_OPTION_E = "-e";

	public static final String GOROOT = "GOROOT";
	public static final String GOARCH = "GOARCH";
	public static final String GOOS = "GOOS";

	/**
	 * defined in plugin.xml
	 */
	public static final String LAUNCH_CONFIGURATION_TYPE = "com.googlecode.goclipse.debug.LaunchConfigurationDelegate";
	/**
	 * used in launch configurations
	 */
	public static final String GO_CONF_ATTRIBUTE_PROJECT = "PROJECT_NAME";
	public static final String GO_CONF_ATTRIBUTE_MAIN = "MAIN_FILE";
	public static final String GO_CONF_ATTRIBUTE_ARGS = "PROGRAM_ARGS";
	public static final String GO_CONF_ATTRIBUTE_BUILD_CONFIG = "BUILD_CONFIG";
	
	public static Map<String, String> environment() {
		Map<String, String> goEnv = new HashMap<String, String>();
		String goroot = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOROOT);
		String goos = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOOS);
		String goarch = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOARCH);

		goEnv.put(GoConstants.GOROOT, goroot);
		goEnv.put(GoConstants.GOOS, goos);
		goEnv.put(GoConstants.GOARCH, goarch);
		validateGoSettings(goroot, goos, goarch);
		return goEnv;

	}
	private static void validateGoSettings(final String goroot, final String goos,
			final String goarch) {
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			public void run() {
				if (goroot == null || goroot.length() == 0 || goos == null
						|| goos.length() == 0 || goarch == null
						|| goarch.length() == 0) {
					MessageDialog messageDialog = new MessageDialog(
							PlatformUI.getWorkbench().getDisplay()
									.getActiveShell(),
							"Goclipse Environment Configuration",
							null,
							"The required Goclipse plug-in variables have not been set.  "
									+ "Please set the variables on the following preferences page.",
							MessageDialog.QUESTION,
							new String[] { IDialogConstants.OK_LABEL }, 0);

					int i = messageDialog.open();

					PreferenceDialog pref = PreferencesUtil
							.createPreferenceDialogOn(PlatformUI.getWorkbench()
									.getDisplay().getActiveShell(),
									GoPreferencePage.ID, null, null);

					if (pref != null) {
						pref.open();
					}
				}// end if
			}// end run
		});

	}
}
