//   Copyright (C) 2005 The Regents of the University of California.
//   All Rights Reserved.
//
//   Permission to use, copy, modify, and distribute this software and its
//   documentation for educational, research and non-profit purposes,
//   without fee, and without a written agreement is hereby granted,
//   provided that the above copyright notice, this paragraph and the
//   following three paragraphs appear in all copies.
//
//   Permission to incorporate this software into commercial products may
//   be obtained by contacting the University of California. For
//   information about obtaining such a license contact:
//
//   Chrisanna Waldrop
//   Copyright Officer
//   805-893-7773
//   waldrop@research.ucsb.edu
//
//   This software program and documentation are copyrighted by The Regents
//   of the University of California. The software program and
//   documentation are supplied "as is", without any accompanying services
//   from The Regents. The Regents does not warrant that the operation of
//   the program will be uninterrupted or error-free. The end-user
//   understands that the program was developed for research purposes and
//   is advised not to rely exclusively on the program for any reason.
//
//   IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
//   FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES,
//   INCLUDING LOST PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND
//   ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF CALIFORNIA HAS BEEN
//   ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. THE UNIVERSITY OF
//   CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
//   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
//   A PARTICULAR PURPOSE. THE SOFTWARE PROVIDED HEREUNDER IS ON AN "AS IS"
//   BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE
//   MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package edu.ucsb.nmsl.autocap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Iterator;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import lk.mrt.cse.pulasthi.autoss.sync.Syncronizer;
import lk.mrt.cse.pulasthi.autoss.tools.SRTTransciptReader;
import lk.mrt.cse.pulasthi.autoss.tools.SRTTransciptWriter;

import edu.ucsb.nmsl.tools.*;
import edu.cmu.sphinx.frontend.util.StreamDataSource;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.decoder.search.PartitionActiveListFactory;
import edu.cmu.sphinx.decoder.search.Token;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.linguist.language.ngram.SimpleNGramModel;

/**
 * This class contains all the logic for AutoCap. AutoCap is an application for
 * the automatic alignment of a segmented transcript with a video for the
 * purpose of providing captions. This class implements the needed event
 * handlers for the Sphinx Speech recognition system for the first process
 * AutoCaptioner the aligns the recognized with the text from the input
 * transcript. After this alignment phase is complete it is necessary to
 * estimate the time-stamps for all captions for which the first word was not
 * recognized. Estimation is accomplished during the estimation phase.
 * 
 * @see "AutoCap: Automatic Captioning of Mutlimedia Presentations"
 * 
 * @author Allan Knight
 * @version 1.0
 * 
 */
public class AutoCaptioner {
	
	private Syncronizer syncronizer;

	/**
	 * This default constructor creates a default instance of AutoCaptioner.
	 * 
	 */
	public AutoCaptioner() {
	}

	/**
	 * This method is called from main to start recognizing a file.
	 * 
	 * @param media
	 *            The file location of the media that is to be aligned with its
	 *            transcript.
	 * @param subFile
	 *            The XML file containing the transcript. See the documentation
	 *            for the class QADTranscriptFileWriter for details on the file
	 *            format for captions used in AutoCap.
	 * 
	 */
	public void start(String media, String subFile) {
		try {
			// Set up the media file
			if (media == null)
				return;

			URL audioURL = new File(media).toURI().toURL();

			// Configure Sphinx based on the config file
			URL configURL = new URL("file:./config/config.xml");

			ConfigurationManager cm = new ConfigurationManager(configURL);
			Recognizer recognizer = (Recognizer) cm.lookup("recognizer");
			PropertySheet activeListProperties = cm
					.getPropertySheet("activeList");
			PropertySheet trigramModelProperties = cm
					.getPropertySheet("trigramModel");

			/* allocate the resource necessary for the recognizer */
			recognizer.allocate();

			AudioInputStream ais = AudioSystem.getAudioInputStream(audioURL);

			System.out
					.println("-----------------------------------------------------");
			System.out.println("Media File: \t" + audioURL.toString());
			System.out.println("Media Format:\t" + ais.getFormat().toString());
			float audioLen = ais.getFrameLength()
					/ ais.getFormat().getFrameRate();
			System.out.println("Media Length: \t" + audioLen + " s");

			System.out.println();

			System.out.println("Config File: \t" + configURL.toString());
			System.out
					.println("ABW Setting: \t"
							+ activeListProperties
									.getRaw(PartitionActiveListFactory.PROP_ABSOLUTE_BEAM_WIDTH));
			System.out.println("Language Model:\t"
					+ trigramModelProperties
							.getRaw(SimpleNGramModel.PROP_LOCATION));

			System.out
					.println("-----------------------------------------------------");
			StreamDataSource reader = (StreamDataSource) cm
					.lookup("streamDataSource");
			reader.setInputStream(ais, audioURL.getFile());

			syncronizer = new Syncronizer();
			// TODO : Read from the SRT reader and store them to use later

			double totalBytes = ais.available();
			final String backspaces = "\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b";
			final String spinner = "-\\|/";
			int count = 0;
			String output = new String();

			System.out.print("\nExtracting text from media file: ");
			// Continue processing file until all the audio has been processed.
			// Another kludge, it's not to easy to tell when and
			// AudioInputStream
			// is done, so we loop until it throws an IOException
			try {
				while (ais.available() > 0) {
					Result res = recognizer.recognize();
					int recognizedAt = (int) (audioLen * (1.0 - (ais
							.available() / totalBytes)));
//					String rec_s = String.format("%2d", recognizedAt % 60);
//					String min = String.format("%2d", (recognizedAt / 60) % 60);
//					String hr = String.format("%2d", recognizedAt / 3600);
//					pw.println(hr + ":" + min + ":" + rec_s + " ==> "
//							+ res.getBestFinalResultNoFiller());
//					pw.flush();
					syncronizer.addDetectedResult(res, recognizedAt);
					System.out.print(backspaces.substring(0, output.length()));
					output = new String(
							(int) (100.0 * (1.0 - (ais.available() / totalBytes)))
									+ "% of audio processed "
									+ spinner.substring(count % 4,
											(count % 4) + 1));
					System.out.print(output);
					++count;
				}
			} catch (IOException i) {
				System.out.println();
				System.out.println("Starting Syncronisation....");
				//TODO: Call syncronizer here
				syncronizer.getSyncronizedTranscipt();
				System.out.println(output + " Done.\n");
					
			}

		} catch (IOException e) {
			System.err.println("Problem when loading AutoCaptioner: " + e);
			e.printStackTrace();
		} catch (PropertyException e) {
			System.err.println("Problem configuring AutoCaptioner: " + e);
			e.printStackTrace();
		} catch (InstantiationException e) {
			System.err.println("Problem creating AutoCaptioner: " + e);
			e.printStackTrace();
		} catch (UnsupportedAudioFileException e) {
			System.err.println("Audio file format not supported.");
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("Caught " + e);
			e.printStackTrace();
		}
	}


	/**
	 * This method is the main method for running the AutoCap application. It is
	 * the only necessary main method in any of the classes that make up the
	 * AutoCap application.
	 * 
	 * @param args
	 *            Array of strings passed from the command line. args[0]
	 *            contains the name of the media file that contains human speeh.
	 *            args[1] the name of the SRT file that contains the transcript.
	 * 
	 */
	public static void main(String[] args) {
		// Create and start the transcriber.
		AutoCaptioner trans = new AutoCaptioner();

		System.out
				.println("\n=====================================================");
		System.out
				.println("=                 Aligning Captions                 =");
		System.out
				.println("=====================================================\n");
		trans.start(args[0], args[1]);
	}
}
