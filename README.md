# ImageUtilities
Library with:

* A high-quality, high-performance image resizer/resampler
* Output dimension calculator
* Custom animation API (and full GIF reading support)

This grew out of a need for a high-quality, real-time image resizing that can compete with programs coded in c++, yet written in pure Java. Considering [java-image-scaling](https://github.com/mortennobel/java-image-scaling) to be dead since 2014, I prepared to develop something on my own. Along the way, I read some websites and books on resizing theory, color theory and concurrency, and implemented those suggestions (e.g. sRGB correction, which all major photo editing programs and browsers do wrong).

While it has tons of features, simply resizing an image is easy with the default settings. The only thing that needs to be done is instantiating a <tt>Resizer</tt>, setting the output size, and invoking the <tt>resize(BufferedImage)</tt> method.

# Features
* Options to configure
  * Output size as resolution (default: <tt>null</tt> = auto)
  * Output size as scaling factor (default: <tt>null</tt> = auto)
  * Output size by both pixels and scaling factor (Image may be cropped or gets a border)
  * Output offset, to move the image with subpixel accuracy (default: depends on final dimension, but is either <tt>(0, 0)</tt> or <tt>(0.5, 0.5)</tt>
  * Flag to skip sRGB correction (default: <tt>false</tt>)
  * Flag to skip alpha premultiplying and un-premultiplying (default: <tt>false</tt>, ignored for opaque images)
  * Mode to handle beyond-edge pixels (default: <tt>CLAMP</tt>, all others are on TODO list)
  * Number of threads (default: <tt>availableProcessors</tt>)
  * Resampling-curve (default: <tt>Lanczos3ResamplingCurve</tt>, others: <tt>BoxResamplingCurve</tt>, <tt>CubicResamplingCurve</tt> and many more, and an easy API to write your own)
* Other features
  * Internal 15 bit per channel encoding to prevent intermediate clamping and quantization
  * Custom containers for Point, Size, etc. which are immutable
  * Progress listeners (custom API for more flexibility)
  * Method to test if input image is in a compatible format (when this returns <tt>false</tt>, resizing <i>that</i> image incurs a conversion penalty) and a utility to convert such images to a compatible format so you can resize them many times, e.g. when resizing a window.
  * GIF loader fixes 'bugged' GIFs to prevent [hyper-speed animations](http://stackoverflow.com/questions/26801433/fix-frame-rate-of-animated-gif-in-java)
  * Performance timer, image analyzer, and other loggable stuff.
  * Auto-select resampling-curve (experimental). This can select a less 'accurate' filter than Lanczos3 depending on the enlargement factor
  * Utilities to calculate output size while keeping aspect ratio, when given constraints like maximum target size, scaling target (Default: <tt>INSIDE</tt>) and condition (default: <tt>ALWAYS</tt>)
  * Utilities to create an <tt>AnimationFrame[]</tt> (read: animation) from GIF files, and a (JavaFX-based) SWING widget to show the animation

The only current implementation of the resampler, <tt>ImageResamplerShort2</tt>, uses a 4-pass strategy:

1. Pre-convert
2. Resample one axis
3. Resample the other axis
4. Post-convert

First it calculates which axis to resize first, based on approximate effort calculations.
Then it splits the image into as many strips as there are CPU threads.
It builds a set of 4&times;N worker jobs.
It determines which jobs need to be finished in one pass before a specific job in the next pass may begin
(because some jobs, namely the Y resample jobs, depend on pixels from other thread's strips).
Note: Strip-dependencies are currently enforced for all strips for debugging purposes.

# Changes
(compared to [java-image-scaling](https://github.com/mortennobel/java-image-scaling))

- UnsharpFilter removed (i.e. DIY if you need it, or just use a sharper Cubic filter)
- DimensionConstrain removed (now static methods in ImageUtilities)
- Changed method/field visibility to prevent synthetic accessor methods
- Subsampling center was way off
- Subsampling center offset now configurable (user and auto<sup>[[2]]( "Sets offset to 0.5 if it would make the image sharper (more precisely, when the output size is an odd number of pixels)")</sup>)
- Subsampling border effect was wrong<sup>[[1]]("Ignoring out-of-bounds pixels increases the weight of existing pixels. This causes the pixels near the edge to 'move' inwards slightly. This is akin to non-linear image warping")</sup> (was: ignore, now: replicate_edge)
- Subsampling left and right always unnecessarily went out of radius (ceil/floor swapped)
- Subsampling numContributors slightly wrong. fNormFac calculation was totally facked. Both are not necessary anymore, because of the next point:
- Subsampling 'number of samples' now fixed for a given scaling factor. Only in rare cases can one of the weights be 0.
- Removed ResampleFilters.java and made individual filters public, so filters can be added/extended easily
- Removed all 'blurry' and 'sharp' filters
- Renamed filters to their popular name (though, without Bi-): Triangle->Linear, Bicubic->Cubic
- Made filters instantiable and moved static instances to each class for singleton access
- Progress calculation was wrong (linear H+W != number of operations H+W)
- Moved filters to own java package
- ExecutorService instead of threads
- Choose horizontally or vertically first for best performance
- Added extensive preprocessing for image type conversions
- No internal clamping or quantization (using short arrays internally)
- sRGB-correct resizing (see http://entropymine.com/imageworsener/gamma)
- Event listener iteration not thread-safe
- Added output scaling mode without specifying explicit output dimensions
- Skip resizing step when one or both dimensions stay the same
- Allow user to both specify output size and scaling factor at the same time, and either can be set back to auto
- Number of threads now configurable (up to availableProcessors)
- Changed vertical resampling algorithm to work with horizontal strips too instead of vertical (up to 4x faster for this step)

# TODO

- New edge mode 'wrap' for tileable images (e.g. game textures)
- New edge mode 'transparent' for icons, etc. that should not have opaque pixels beyond the edges
- New edge mode 'bounce'
- Subpixel resizing for TFT displays
- Low-memory version
- Fast versions (<tt>byte</tt> instead of <tt>short</tt> internal representation)
- More radius-1 windowed-cosine filters (from signal processing)
