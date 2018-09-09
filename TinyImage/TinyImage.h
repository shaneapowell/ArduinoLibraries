/*********************************************************************
 * Tiny Image
 * 
 * Sometimes when using basic bitmap image data inside a small IOT device,
 * we run out of PROGMEM.  Installing a libpng library and dealing with
 * compressed images in SPIFS or the SD Card, or even PROGMEM can work 
 * out just fine, if you have the time and ram to decode the PNG on the cpu.
 * I built a POV clock that displays lots of different 64x64 images.  
 * Despite the reasonable amount of PROGMEM, I quickly used it up. Even
 * when implementing a simple 8-bit pallet lookup bitmap format.
 * The good news is, most of my images were simple color palets with a handfull
 * of colors, and lots of 1 color.  This library will turn a simple PNG
 * into a PROGMEM .h file that is not only very fast to index and lookup, but
 * more importantly, VERY small in size.  
 * I managed to turn a 12k 64x64 bitmap into ??
 *********************************************************************/ 

#ifndef __TINY_IMAGE_H__
#define __TINY_IMAGE_H__

#include <Arduino.h>
#include <stdint.h>

#define TINYIMAGE_DATA_LEN(x)  (sizeof(x) / sizeof(x[0]))

/* A simple 3 element struct to describe a sequential row of pixel colors */
typedef struct
{
	uint16_t pixelIndex;	/* The starting inclusive index of this sequence of pixels */
	uint16_t pixelCount;	/* The number of pixels in this sequence */
	uint16_t palletIndex;	/* The color of this sequence */
} TinyImageData;

/* A wrapper struct around all the info that describes a single TinyImage */
typedef struct 
{
	const TinyImageData *data;
	uint16_t dataLength;
	const uint32_t *pallet;
	uint16_t width;
	uint16_t height;
} TinyImage;


/*************************************************************
 * Get the pixel color value at the specified image X Y coordinates.
 ************************************************************/
uint32_t tinyImageGetPixel(const TinyImage *image, uint16_t x, uint16_t y)
{
	if (x >= 0 && x < image->width && y >= 0 && y < image->height)
	{
		int pixelIndex = y * image->width + x;
		TinyImageData imageData;
		TinyImageData d;


		/* Walk backwards down the data list */
		for (int index = image->dataLength-1; index >= 0; index--)
		{
			imageData = image->data[index];

			/* If this data elements start index is before our pixel index */
			if (pixelIndex >= imageData.pixelIndex)
			{

				/* And the data length is after our index */
				if (pixelIndex < imageData.pixelIndex + imageData.pixelCount)
				{
					/* We have a match */
					return image->pallet[imageData.palletIndex];
				}
			}
		}

	}

	/* If no pixel match is ever made, we return the 1st pallet color */
	return image->pallet[0];

}


#endif