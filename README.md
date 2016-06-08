# ImageUtilities
Library with:

* A high-quality, high-performance image resizer/resampler
* Output dimension calculator
* Custom animation API (and full GIF reading support)

This grew out of a need for a high-quality, real-time image resizing that can compete with programs coded in c++, yet written in pure Java. Along the way, I read some websites and books on resizing theory, color theory and concurrency, and implemented those suggestions (e.g. sRGB correction, which all major photo editing programs and browsers do wrong).

While it has tons of features, simply resizing an image is easy with the default settings. The only thing that needs to be done is instantiating a <tt>Resizer</tt>, setting the output size, and invoking the <tt>resize(BufferedImage)</tt> method.

# Features
* Options to configure
  * Output size as resolution (default: <tt>null</tt> = auto)
  * Output size as scaling factor (default: <tt>null</tt> = auto)
  * Output size by both pixels and scaling factor (Image may be cropped or gets a border)
  * Output offset, to move the image with subpixel accuracy (default: depends on final dimension, but is either <tt>(0, 0)</tt> or <tt>(0.5, 0.5)</tt>
  * Flag to skip sRGB correction (default: <tt>false</tt>)
  * Flag to skip alpha pre-multiplying and un-pre-multiplying (default: <tt>false</tt>, ignored for opaque images)
  * Mode to handle out-of-edge pixels (default: <tt>CLAMP</tt>, all others are on TODO list)
  * Number of threads (default: <tt>availableProcessors</tt>)
  * Resampling-curve (default: <tt>Lanczos3ResamplingCurve</tt>, others: <tt>BoxResamplingCurve</tt> and <tt>CubicResamplingCurve</tt> and many more, and an easy API to write your own)
* Other features
  * Internal 15 bit per channel format to prevent intermediate clamping
  * Custom containers for Point, Size, etc. which are immutable
  * Progress listeners (custom API for more flexibility)
  * Method to test if input image is in a compatible format (when this returns <tt>false</tt>, resizing <i>that</i> image incurs a conversion penalty) and a utility to convert such images to a compatible format.
  * Auto-select resampling-curve (experimental). This can select a less 'accurate' filters than Lanczos3 depending on the enlargement factor
  * GIF loader fixes 'bugged' GIFs to prevent [hyper-speed animations](http://stackoverflow.com/questions/26801433/fix-frame-rate-of-animated-gif-in-java)
  * Performance timer (spams some debug stuff to the console while resizing)
  * Utilities to calculate output size while keeping aspect ratio, when given constraints like maximum target size, scaling target (Default: <tt>INSIDE</tt> and condition (default: <tt>ALWAYS</tt>)
  * Utilities to create an <tt>AnimationFrame[]</tt> (read: animation) from GIF files, and a (JavaFX-based) SWING widget to show the animation

Currently, two concrete implementations of the resampler are available, the slightly slower but more mature <tt>ImageResamplerShort2</tt> which uses a 4-pass strategy:

1. Pre-convert
2. Resample one axis
3. Resample the other axis
4. Post-convert

Each step is performed by slicing the image up in N strips and calculating those concurrently. Each next step only starts when the entire previous step is completed. On average (tested on an idle i7-4710MQ) about 5-10% of CPU cycles is wasted because the fastest thread outperforms the slowest thread by that amount.

---

The slightly faster and experimental implementation, <tt>ImageResamplerShort</tt> uses a smart interleaving strategy. The image is chopped into many rectangular pieces, and workers for each of the four steps are instantiated for each piece, with a dependency on the results of the previous worker and neighboring workers. A worker becomes eligible for execution if all dependent workers are completed. All workers are then executed concurrently. Workers which are blocked start whenever they become eligible <i>and</i> a thread is available.

# Changes
(compared to [java-image-scaling](https://github.com/mortennobel/java-image-scaling))

- UnsharpFilter removed (i.e. DIY if you need it, or just use a sharper Cubic filter)
- DimensionConstrain removed (now static methods in ImageUtilities)
- Changed method/field visibility to prevent synthetic accessor methods
- Subsampling center was way off
- Subsampling center offset now configurable (user and auto<sup>[[2]]( "Sets offset to 0.5 if it would make the image sharper (more precisely, when the output size is an odd number of pixels)")</sup>)
- Subsampling border effect was wrong<sup>[[1]]( "Ignoring out-of-bounds pixels increases the weight of existing pixels. This causes the pixels near the edge to 'move' inwards slightly. This is akin to non-linear image warping")</sup> (was: ignore, now: replicate_edge)
- Subsampling left and right always unnecessarily went out of radius (ceil/floor swapped)
- Subsampling numContributors slightly wrong. fNormFac was facked. Both are not necessary anymore, because of:
- Subsampling 'number of samples' now fixed for each direction. In rare cases one of the weights can be 0.
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
