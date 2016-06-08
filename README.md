# ImageUtilities
Library with:

* A high-quality, high-performance image resizer/resampler
* Size calculator
* Custom animation API (and full GIF reading support)

This grew out of a need for a high-quality, real-time image resizing that can compete with programs coded in c++ or Python, yet written in pure Java. Along the way, I read some websites and books on resizing theory, color theory and concurrency, and implemented those suggestions (e.g. sRGB correction, which all major photo editing programs do wrong).

While it has tons of featues, simply resizing an image is easy with the default settings. The only thing that needs to be done is instantiating a <tt>Resizer</tt>, setting the output size, and invoke the <tt>resize(BufferedImage)</tt> method.

# Features
* Options to configure
  * Output size as resolution (default: <tt>null</tt> = auto)
  * Output size as scaling factor (default: <tt>null</tt> = auto)
  * Output size by both pixels and scaling factor (Image may be cropped or gets a border)
  * Output offset, to move the image with subpixel accuracy (default: depends on final dimension, but is either <tt>(0, 0)</tt> or <tt>(0.5, 0.5)</tt>
  * Flag to skip sRGB correction (default: <tt>false</tt>)
  * Flag to skip alpha pre-mupltiplying and un-pre-multiplying (default: <tt>false</tt>, ignored for opaque images)
  * Mode to handle out-of-edge pixels (default: <tt>CLAMP</tt>, others: <tt>WRAP</tt>, <tt>BOUNCE</tt>, <tt>TRANSPARENT</tt>)
  * Number of threads (default: <tt>availableProcessors</tt>)
  * Resampling-curve (default: <tt>Lanczos3ResamplingCurve</tt>, others: <tt>BoxResamplingCurve</tt> and <tt>CubicResamplingCurve</tt> and many more, and an easy API to write your own)
* Other features
  * Custom containers for Point, Size, etc. which are immutable
  * Progress listeners (custom API for more flexibility)
  * Method to test if input image is in a compatible format (when this returns <tt>false</tt>, resizing <i>that</i> image incurs a conversion penalty)
  * Auto-select resampling-curve (experimental). This can select a less 'accurate' filters than Lanczos3 depending on the enlargement factor
  * Performance timer (spams some stuff to stdout)
  * Internal 15 bit per channel format to prevent intermediate clamping
  * Utilities to calculate output size while keeping aspect ratio, when given constraints like maximum target size, scaling target (Default: <tt>INSIDE</tt> and condition (default: <tt>ALWAYS</tt>)
  * Utilities to create an <tt>AnimationFrame[]</tt> (read: animation) from GIF files, and a (JavaFX-based) SWING widget to show the animation
  * Automatically fixes 'bugged' GIF to prevent [hyperspeed animations](http://stackoverflow.com/questions/26801433/fix-frame-rate-of-animated-gif-in-java)

Two concrete implementations of the resampler are implemented, the slightly older <tt>ImageResamplerShort2</tt> which uses a 4-pass strategy:

1. Pre-convert
2. Resample one axis
3. Resample the other axis
4. Post-convert

Each step is performed by slicing the image up in N strips and calculating those concurrently. Each next step only starts when the entire previous step is completed. On average (tested on an idle i7-4710MQ) about 5-10% of CPU cycles is wasted because the fastest thread outperforms the slowest thread by that amount.

---

The newest, still experimental, implementation, <tt>ImageResamplerShort</tt> uses a smart intetrleaving strategy. The image is chopped into many rectangular pieces, and workers for each of the four steps are instantiated for each piece, with a dependency on the results of the prevous worker and neighboring workers. A worker becomes eligible for execution if all dependent workers are completed. All workers are then executed concurrently. Workers which are blocked start whenever they become eligible <i>and</i> a thread is available.

# Changes
(compared to [java-image-scaling](https://github.com/mortennobel/java-image-scaling))

- UnsharpFilter removed (i.e. DIY if you need it, or just use a sharper Cubic filter)
- DimensionConstrain removed (now static methods in ImageUtilities)
- Changed method/field visibility to prevent synthetic accessor methods
- Subsampling center offset was way off
- Subsampling border effect was wrong<sup>[[1]]( "Ignoring out-of-bounds pixels increases the weight of existing pixels. This causes the pixels near the edge to 'move' inwards slightly. This is akin to non-linear image warping")</sup> (was: ignore, now: replicate_edge)
- Subsampling left and right always unnecessarily went out of radius (ceil/floor swapped)
- Subsampling numContributors slightly wrong. fNormFac was facked. Both are not necessary anymore, because of:
- Subsampling 'number of samples' now fixed for each direction. In rare cases one of the weights can be 0.
- Removed ResampleFilters.java and made individual filters public, so filters can be added/extended easily
- Removed all 'blurry' and 'sharp' filters
- Renamed filters to their popular name (without Bi-): Box->Nearest, Triangle->Linear, Bicubic->Cubic
- Made filters instantiable and moved static instances to each class for singleton access
- Progress calculation was wrong (linear H/W != number of operations H/W)
- Moved filters to own java package
- ExecutorService instead of threads
- Choose horizontally or vertically first for best preformance
- Added extensive preprocessing for image type conversions
- No internal clamping or quantization (using short arrays internally)
- sRGB-correct resizing (see http://entropymine.com/imageworsener/gamma)
- sRGB-correction can be ignored to speed up resizing
- Event listener iteration not thread-safe
- Added output scaling mode without specifying explicit output dimensions
- Skip resizing step when one or both dimensions stay the same
- Allow user to both specify output size and scaling factor at the same time, and to set either back to auto
- Number of threads now configurable (up to availableProcessors)
- Subsampling center offset now configurable (user and auto<sup>[[2]]( "Sets offset to 0.5 if it would make the image sharper (more precisely, when the output size is an odd number of pixels)")</sup>)
- Changed vertical resampling algorithm to work with horizontal strips instead of vertical (speed-up: 4x)
    
# TODO
New border effect 'wrap' for tileable images (e.g. game textures)
New border effect 'transparent' for icons, etc. that should not have opaque pixels beyond the edges.
Split image into tiles to make workers more fine-grained
Reimplement Progress calculation
Subpixel resizing for TFT displays (opaque only)
Interleave horizontal and vertical processing to prevent intermediate thread rendevouz
Low-memory version
Fast versions
Automatic-algorithm-choosing wrapper
More radius-1 windowed-cosine filters (from signal processing)
Resizing animated GIFs (and MNGs?)
