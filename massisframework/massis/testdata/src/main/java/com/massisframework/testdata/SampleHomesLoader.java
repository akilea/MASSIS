package com.massisframework.testdata;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.event.ListSelectionEvent;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.massisframework.testdata.gui.SampleHomeGUIControl;
import com.massisframework.testdata.gui.SampleHomesGUI;

/**
 * Class for loading sample homes, included in com.massisframework.testdata
 * 
 * @author rpax
 *
 */
public class SampleHomesLoader {

	public static final String SAMPLES_BUILDING_DIR = "com/massisframework/testdata/";
	private static final Gson gson = new GsonBuilder().setPrettyPrinting()
			.create();

	/**
	 * Loads a sample home.
	 * 
	 * @param homeName
	 *            the name of the home. The name should be contained in the list
	 *            returned by {@link #listAvailable()}
	 * @throws IOException
	 * @throws RecorderException
	 */
	public static File loadHomeTempFile(String homeName) throws IOException

	{
		//Safer
		homeName=FilenameUtils.removeExtension(homeName);
		final SampleHomeDescription desc = loadDescription(homeName);

		/*
		 * Copy home file to temporary folder & load it.
		 */
		final File destFile = OperatingSystem
				.createTemporaryFile("MASSIS_", ".sh3d");
		try (InputStream is = SampleHomesLoader.class.getClassLoader()
				.getResourceAsStream(SAMPLES_BUILDING_DIR + desc.getFilename()))
		{
			try (FileOutputStream os = new FileOutputStream(destFile))
			{
				IOUtils.copy(is, os);
			}
		}
		destFile.deleteOnExit();
		return destFile;
	}

	/**
	 * @return a list of homes that this loader can load.
	 */
	public static List<SampleHomeDescription> listAvailable()
	{
		final List<SampleHomeDescription> available = new ArrayList<>();
		try (InputStream is = SampleHomesLoader.class.getClassLoader()
				.getResourceAsStream(SAMPLES_BUILDING_DIR))
		{
			if (is != null)
			{
				final LineIterator it = IOUtils.lineIterator(is,
						Charsets.UTF_8);
				while (it.hasNext())
				{
					final String line = it.nextLine();
					System.err.println(line);
					/*
					 * Filder by extension
					 */
					if (line.endsWith(".json"))
					{
						available.add(
								loadDescription(line.replace(".json", "")));
					}
				}

			} else
			{
				Logger.getLogger(SampleHomesLoader.class.getName()).log(
						Level.WARNING, "Invalid directory");
			}
		} catch (final IOException e)
		{
			Logger.getLogger(SampleHomesLoader.class.getName()).log(
					Level.SEVERE, "Error when listing available buildings", e);
		}
		return available;
	}

	public static SampleHomeDescription loadDescription(String homeName)
			throws IOException
	{
		//Safer
				homeName=FilenameUtils.removeExtension(homeName);
		try (InputStream is = SampleHomesLoader.class.getClassLoader()
				.getResourceAsStream(
						SAMPLES_BUILDING_DIR + homeName + ".json"))
		{
			final String jsonContent = IOUtils.toString(is);
			return gson.fromJson(jsonContent, SampleHomeDescription.class);
		}
	}

	public static void main(String[] args)
	{

		final SampleHomesGUI frame = new SampleHomesGUI(getSampleGUIControl());

		final ArrayList<String> listValues = new ArrayList<>();
		final List<SampleHomeDescription> descriptions = SampleHomesLoader
				.listAvailable();
		for (final SampleHomeDescription homeDesc : descriptions)
		{
			listValues.add(homeDesc.getFilename());

		}
		frame.setHomeListValues(listValues);
		frame.setVisible(true);

		// Tendria que hacerlo el controlador

	}

	private static void updateGUI(SampleHomesGUI gui)
	{
		final String selectedHomeName = gui.getSelectedHomeName();
		try
		{
			final SampleHomeDescription desc = SampleHomesLoader
					.loadDescription(selectedHomeName);
			gui.setImage(desc.getImage());
			gui.setDescription(desc.getDescription());
		} catch (final IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static SampleHomeGUIControl getSampleGUIControl()
	{
		return new SampleHomeGUIControl() {

			@Override
			public void sampleHomesListValueChanged(
					SampleHomesGUI gui,
					ListSelectionEvent evt)
			{
				if (evt.getValueIsAdjusting())
				{
					return;
				}
				updateGUI(gui);

			}

			@Override
			public void loadHomeButtonActionPerformed(SampleHomesGUI aThis,
					ActionEvent evt)
			{
				System.out.println("Click");

			}
		};
	}
}
