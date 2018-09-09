

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.imageio.ImageIO;

/**************************************
 * An instance of a single TinyImage, that
 * will output all the parts of the .h file.
 **************************************/
public class TinyImage
{

	private static final String ERR = "Err: ";
	private static final String NL = "\n";
	private static final String PALLET_PREFIX 	= "pallet_data %s";
	private static final String DATA_PREFIX		= "image_data_%s";

	private static final String TEMPLATE_TOP_F3 =
		"/***********************************************"
+ NL + " * FILE: %1$s"
+ NL + " * DATE: %2$s"
+ NL + " **********************************************/"  
+ NL + "#ifndef %3$s"
+ NL + "#define %3$s"
+ NL 
+ NL + "#include \"TinyImage.h\"" 
+ NL 
+ NL;


	private static final String TEMPLATE_BOTTOM =
		"#endif" ;


	public String mFileName;
	public String mVarName;
	public ArrayList<Integer> mPallet = new ArrayList<Integer>();
	public ArrayList<ImageSegment>  mSegments = new ArrayList();
	public int mWidth;
	public int mHeight;
	public int mPixelCount;

	/****************************************************
	 * 
	 ****************************************************/
	public TinyImage(String fileName, BufferedImage img) throws Exception
	{
		this.mFileName = fileName;
		this.mVarName = fileName.substring(0, fileName.indexOf('.'));
		this.mWidth = img.getWidth();
		this.mHeight = img.getHeight();
		this.mPixelCount = this.mWidth * this.mHeight;

		ImageSegment currentSegment = null;

		/* Generate a complete segment list */
		for (int row = 0; row < this.mHeight; row++)
		{
			/* and each "col" */
			for (int col = 0; col < this.mWidth; col++)
			{
				int thisRGB = img.getRGB(col, row);
				thisRGB = thisRGB & 0x00ffffff;

				/* If we're at a new RGB value in our list of pixels, we need a new data element */
				if (currentSegment == null || thisRGB != currentSegment.mRGB)
				{
					currentSegment = new ImageSegment(row * this.mWidth + col, thisRGB);
					this.mSegments.add(currentSegment);
				}
				
				currentSegment.increment();
			}
		}


		/* Count the various RGB color segments. The highest one will be color index 0 */
		int highestSegmentCount = -1;
		int rgbWithHighestSegmentCount = Integer.MAX_VALUE;
		HashMap<Integer, Integer> rgbCount = new HashMap<>();
		for (ImageSegment segment : this.mSegments)
		{
			int rgb = segment.mRGB;
			int count = 0;
			if (rgbCount.containsKey(rgb))
			{
				count = rgbCount.get(rgb);
			}
			count++;
			rgbCount.put(rgb, count);

			/* Track the one with the highest count */
			if (count > highestSegmentCount)
			{
				highestSegmentCount = count;
				rgbWithHighestSegmentCount = rgb;
			}
		}
		this.mPallet.add(rgbWithHighestSegmentCount);


		/* Now, add to our pallet all un-added colors, and assign the palet index to the segment */
		for (ImageSegment segment : this.mSegments)
		{
			if (mPallet.contains(segment.mRGB) == false)
			{
				mPallet.add(segment.mRGB);
			}

			segment.mPalletIndex = mPallet.indexOf(segment.mRGB);

		}

		/* Remove all segments with color index 0 */
		ArrayList<ImageSegment> trimmedSegments = new ArrayList<>();
		for (ImageSegment seg : this.mSegments)
		{
			if (seg.mPalletIndex != 0)
			{
				trimmedSegments.add(seg);
			}
		}
		this.mSegments = trimmedSegments;
		
	
	}

	/*************************************
	 * Output the C header variant
	 ************************************/
	public void generateCHeader(PrintStream out, boolean includeFileWrappers)
	{
		String define = "__" + this.mFileName.replace('.', '_') + "__".toUpperCase();

		if (includeFileWrappers)
		{
			out.println(String.format(TEMPLATE_TOP_F3, this.mFileName, new java.util.Date(), define));
		}

		out.print(NL);

		/* Pallet */
		out.print(String.format("const uint32_t pallet_data_%1$s[] PROGMEM = {", this.mVarName));
		for (int index = 0; index < this.mPallet.size(); index++)
		{
			if (index % 10 == 0)
			{
				out.print(NL + "    ");
			}

			out.print("0x" + Integer.toHexString(this.mPallet.get(index)) + ",");
		}
		out.println(NL + "};" + NL);

		/* Data */
		out.print(String.format("const TinyImageData image_data_%1$s[] PROGMEM = {", this.mVarName));
		for (int index = 0; index < this.mSegments.size(); index++)
		{
			if (index % 10 == 0)
			{
				out.print(NL + "    ");
			}

			ImageSegment segment = this.mSegments.get(index);
			out.print(segment.toString());
			
		}
		out.println(NL + "};" + NL);

		/* Instance */
		out.print(String.format("const TinyImage image_%1$s PROGMEM = { image_data_%1$s, %2$d,  pallet_data_%1$s, %3$d, %4$d }; ", 
								this.mVarName, 
								this.mSegments.size(), 
								this.mWidth, 
								this.mHeight));
		out.print(NL + NL);

		if (includeFileWrappers)
		{
			out.println(TEMPLATE_BOTTOM);
		}

	}


	/***********************************************
	 * 
	 ***********************************************/
	private static class ImageSegment
	{
		public int mImageIndex;
		public int mPixelCount = 0;
		public int mRGB;
		public int mPalletIndex;
	
		/*************************************/
		public ImageSegment(int iIndex, int rgb)
		{
			this.mImageIndex = iIndex;
			this.mRGB = rgb;
		}
		
		/*************************************/
		public void increment() { this.mPixelCount++; }
	
	
		/*************************************/
		public String toString()
		{
			return String.format("{%d, %d, %d},", mImageIndex, mPixelCount, mPalletIndex);
		}
	}


	/****************************************
	 * Main Entry.
	 * @param args
	 ***************************************/
	public static void main(String[] args)
	{
		Options options = new Options();
		options.addOption("h", false, "Print this Message");
		options.addOption("i", "input", true, "Input File");
		options.addOption("d", false, "Variables and Data Only");


		try
		{

			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption('h'))
			{
				HelpFormatter formater = new HelpFormatter();
				formater.printHelp("TinyImage -i <file>", options);
				return;
			}

			String srcFile = cmd.getOptionValue('i');

			File sf = new File(srcFile);
			if (!sf.exists())
			{
				System.out.println("ERROR [" + srcFile + "] not Found");
			}
			else if (!sf.canRead())
			{
				System.out.println("ERROR [" + srcFile + "] unable to read file");
			}


			TinyImage img = new TinyImage(srcFile, ImageIO.read(sf));
			img.generateCHeader(System.out, !cmd.hasOption('d'));

		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}
}

