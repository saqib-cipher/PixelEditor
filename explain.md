# PixelEditor — Project Architecture & File/Function Reference

This document provides a concise summary of every file, class, and method in the **PixelEditor** codebase.

---

## 1. Core Editor & Orchestration (`glab.pixeleditor`)

### [`MainActivity.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/MainActivity.java)
The primary workspace activity. Orchestrates the interactive canvas, multi-panel bottom dock, toolbars, dialogs, and effect browsers.
- **Lifecycle & Setup**:
  - `onCreate(Bundle)`: Initializes edge-to-edge window insets, loads or creates `EditorProject`, sets up views.
  - `setupWindowInsets()`: Adjusts top bar height dynamically for camera notch/status bar insets and bottom container for navigation bars.
  - `setupCanvas()`: Configures `PixelCanvasView` callbacks for layer selection, modifications, and viewport resets.
  - `setupTopBar()`: Back navigation, project renaming dialog, export/share sheet, project settings dialog.
  - `setupMidToolbar()`: Wires floating left-side tools (Undo, Redo, Copy/Duplicate, Delete).
  - `updateCanvasToolsState()`: Updates alpha/enabled states of Undo, Redo, Copy, and Delete buttons.
- **Panel System (`ViewFlipper` / Bottom Dock)**:
  - `setupLayerMenu()`: Main 6-tile grid actions: *Color & Fill*, *Border & Shadow*, *Blending & Opacity*, *Move & Transform*, *Edit Shape / Size*, *Effects*.
  - `setupColorFillPanel()`: Fill color, solid/gradient palette picker, opacity slider.
  - `setupBorderShadowPanel()`: Stroke toggle, stroke width slider, stroke color, shadow toggle/radius/color.
  - `setupEditShapePanel()`: Custom shape param sliders/spinners parsed from shape XML CDATA, shape type selector.
  - `setupPhotoAdjustPanel()`: Photo width/height dimension sliders and preset filters.
  - `setupMoveTransformPanel()`: Multi-mode transform controls:
    - *Position*: Touchpad with swipe gesture for Z-order reordering and magnetic snapping.
    - *Rotate*: Circular dial rotation.
    - *Scale*: Scrub ruler for width/height scale.
    - *Skew*: Scrub ruler for horizontal/vertical skewing.
  - `populateLayersOverviewPanel()`: Vertical timeline layer stack with drag-to-reorder via `ItemTouchHelper`, lock/unlock state toggle with real-time canvas interaction guards, visibility toggle, and type-specific vector/preset icons.
- **Effects Integration**:
  - `setupEffectBrowser()`: Full-screen searchable Alight Motion effect category browser.
  - `populateEffectControlsPanel(EffectDefinition)`: Dynamically generates UI controls for effect parameters (sliders, switches, colors).
- **Import & Export**:
  - `exportProjectToGallery()`: Renders artboard offscreen at full resolution and saves JPEG/PNG to device storage.
  - `showSmartExitDialog()`: Prompts to save changes before exiting.

---

## 2. Interactive Canvas & Custom Views (`glab.pixeleditor.view`)

### [`PixelCanvasView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/view/PixelCanvasView.java)
The custom viewport rendering the artboard, background, layers, selection bounding boxes, transformation handles, on-canvas gradient vector controller, and magnetic guidelines.
- `resetViewport()`: Centers and scales the artboard to fit screen bounds with minimal margins (maximizing height).
- `handleResize(layer, cdx, cdy)`: Scales proportionally (aspect ratio locked) when dragging the 4 corner handles (Top-Left, Top-Right, Bottom-Left, Bottom-Right), and independently adjusts width or height when dragging the side handles.
- `drawGradientControllerOverlay(Canvas, ShapeLayer)`: Renders on-canvas gradient vector line connecting Start Handle (circle ring with start color) and End Handle (circle ring with end color) directly over the active shape.
- `drawEyedropperLoupe(Canvas)`: Renders real-time circular magnifier loupe with crosshair, sampled color swatch disc, and floating hexadecimal label (`#AARRGGBB`) during active eyedropper sampling.
- `onDraw(Canvas)`:
  - Draws canvas backdrop and dropshadow.
  - Renders all visible layers in Z-order with transformations and shaders applied.
  - Draws selection bounding box, corner scale handles, and top rotation stem.
  - Draws on-canvas gradient handles and vector line when active layer has gradient fill.
  - Draws eyedropper loupe during color sampling.
  - Draws magnetic snap guidelines when aligning elements.
- `onTouchEvent(MotionEvent)`:
  - Detects touch targets: gradient start handle, gradient end handle, corner resize handles, rotation handle, layer body, or empty canvas space.
  - Directly maps touch motion in view coordinates to layer local coordinates for interactive gradient angle, radius, and length repositioning.
  - Handles real-time multi-touch gesture dragging, scaling, and rotation.
  - Automatically saves single undo snapshots upon drag start.
- `applyMagneticSnapping(CanvasLayer, rawX, rawY)`: Applies 8dp magnetic snapping to artboard center/edges and sibling layer bounds.
- `clearMagneticSnapLines()`: Clears active guide lines when touch gesture finishes.

### [`GradientBarView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/view/GradientBarView.java)
Interactive horizontal gradient track with draggable start and end color stop thumbs.
- `setColors(int startColor, int endColor)`: Updates gradient track preview and thumb fill colors.
- `setOffsets(float startOffset, float endOffset)`: Positions start stop ($0.0-1.0$) and end stop ($0.0-1.0$).
- `onTouchEvent(MotionEvent)`: Enables fluid dragging of individual color stop thumbs and track tap positioning, reporting real-time offsets via `OnGradientChangeListener`.

### [`ClampedSliderView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/view/ClampedSliderView.java)
Custom Material 3 Expressive slider with strict boundary clamping.
- `setValue(float)` / `setValueFrom(float)` / `setValueTo(float)`: Configures range and current value.
- `updateValueFromTouch(touchX, paddingStart, trackWidth)`: Strictly clamps touch fraction between 0.0 and 1.0 (cannot drag past bounds) and emits haptic tick feedback (`CLOCK_TICK`) when hitting boundaries.
- `onDraw(Canvas)`: Renders background track, active accent track, thumb body, and glowing touch halo.

### [`ScrubRulerView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/view/ScrubRulerView.java)
Infinite or bounded horizontal scrub ruler with major/minor tick marks and haptic feedback.
- `setBounds(min, max)`: Enables boundary clamping.
- `onTouchEvent(MotionEvent)`: Tracks horizontal drag delta `dx`, enforces min/max bounds, triggers haptic feedback, and invokes `OnScrubListener`.

### [`CircularDialView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/view/CircularDialView.java)
Rotatable circular dial widget for 360-degree layer angle manipulation.
- `setAngle(float)` / `getAngle()`: Gets/sets current rotation angle in degrees.
- `onTouchEvent(MotionEvent)`: Computes polar angle `atan2(y, x)` relative to dial center and invokes `OnAngleChangeListener`.

---

## 3. Layer Models & Project State (`glab.pixeleditor.model`)

### [`CanvasLayer.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/CanvasLayer.java)
Abstract base class for all canvas elements.
- **Properties**: `id`, `name`, `x`, `y`, `width`, `height`, `rotation`, `scaleX`, `scaleY`, `skewX`, `skewY`, `opacity`, `isVisible`, `isLocked`, `appliedEffects`, `borders` (List<BorderItem>), `shadows` (List<ShadowItem>).
- `draw(Canvas)`: Abstract render method implemented by each subclass.
- `copy()`: Creates a user-duplicated copy with `+30, +30` offset and `" Copy"` suffix.
- `cloneLayer()`: Creates an identical clone preserving exact coordinates, name, ID, borders, shadows, and effects (used for Undo/Redo snapshot integrity).
- `toJson(Context)` / `fromJson(JSONObject, Context)`: Serialization/deserialization with borders and shadows.

### [`BorderItem.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/BorderItem.java)
Data model for a single border/stroke element on a layer.
- Supports `INSIDE`, `CENTER`, `OUTSIDE` alignments.
- Properties: `enabled`, `color`, `width`, `alignment`, `cap` (`Paint.Cap`), `join` (`Paint.Join`).
- `copy()` / `cloneItem()` / `toJson()` / `fromJson()`.

### [`ShadowItem.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/ShadowItem.java)
Data model for a single dropshadow element on a layer.
- Properties: `enabled`, `color`, `size` (blur radius $0-100\text{px}$), `alpha` ($0.0-1.0$), `offsetX` ($-100$ to $+100\text{px}$), `offsetY` ($-100$ to $+100\text{px}$).
- `copy()` / `cloneItem()` / `toJson()` / `fromJson()`.

### [`PhotoLayer.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/PhotoLayer.java)
Bitmap image layer with color adjustment filters and shader effect pipelines.
- `draw(Canvas)`: Applies matrix transformations, layer opacity, color filters (brightness, contrast, saturation, warmth), and GL shader effects via `EffectPipeline.processLayerEffects(...)`.
- `cloneLayer()` / `copy()`: Clones bitmap reference and filter properties.

### [`ShapeLayer.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/ShapeLayer.java)
Vector geometry layer (Rectangle, Circle, Star, Polygon, Teardrop, Crescent, Arrow, etc.).
- `draw(Canvas)`: Evaluates shape path geometry via `ShapeGeometryHelper`. Renders multi-shadows underneath (`BlurMaskFilter`), fill with alpha calculation (fixing transparent fill bugs), primary stroke with inside/center/outside alignments and join caps, and all configured `borders` with distinct alignments.
- `setShapeDefinition(ShapeDefinition)`: Binds XML-defined procedural shape parameters.
- `cloneLayer()` / `copy()`: Preserves shape definitions, fill, stroke, borders, shadows, and effect states.

### [`TextLayer.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/TextLayer.java)
Text layer with styling, typography, stroke, shadows, fill modes (Solid, Gradient, Media Image texture, None), and text-specific effect transforms.
- `draw(Canvas)`: Renders text with active `FillMode`:
  - **Solid Fill**: Single color with alpha transparency.
  - **Gradient Fill**: Configurable `LinearGradient`, `RadialGradient`, or `SweepGradient` shaders using start/end colors and offsets.
  - **Media / Image Texture Fill**: Applies `BitmapShader` with `FILL` (center-crop), `FIT`, or `STRETCH` scaling modes over text glyphs.
  - **None**: Transparent fill (outline/stroke/shadows only).
  - Handles text layout, baseline positioning, font styles, text modifiers (Text Transform, Text Progress, Randomize), and offscreen GL shader effect pipeline.
- `recalculateBounds()`: Computes precise text width and font metrics height.
- `toJson(Context)` / `fromJson(Context, JSONObject)` / `copy()` / `cloneLayer()`: Preserves fill modes, gradient coordinates, gradient type, and media image fill URI across project saves and undo/redo snapshots.

### [`EditorProject.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/EditorProject.java)
Represents a project canvas, layers, metadata, and history.
- `saveSnapshot()`: Saves current state to `undoStack` using `cloneLayer()`, clearing `redoStack`.
- `undo()` / `redo()`: Pops previous/next state snapshots and updates `layers` and `selectedIndex`.
- `addLayer(layer)` / `removeLayer(index)` / `duplicateLayer(index)` / `moveLayerUp(index)` / `moveLayerDown(index)`: Layer stack mutations.
- `toJson(Context)` / `fromJson(JSONObject, Context)`: Serializes artboard metadata and layers.

### [`ProjectStorageManager.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/model/ProjectStorageManager.java)
Persistence manager saving/loading projects as JSON and thumbnail previews on local device storage.
- `saveProject(Context, EditorProject)`: Serializes project JSON and saves a PNG thumbnail.
- `loadProject(Context, projectId)`: Loads and deserializes project JSON.
- `getAllProjects(Context)`: Lists all saved project summaries for home feed.
- `deleteProject(Context, projectId)`: Deletes project directory and assets.

---

## 4. Effect Engine & GLSL Shaders (`glab.pixeleditor.effect`)

### [`GLEffectEngine.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/effect/GLEffectEngine.java)
High-performance headless OpenGL ES 2.0 / EGL 14 offscreen evaluation engine for Alight Motion effect shaders.
Headless OpenGL ES 2.0 / 3.0 render engine with EGL context management:
- `ensureContext()`: Initializes headless EGL display, Pbuffer surface, and GL context.
- Maintains FBO textures, quad VBOs, and compiles custom GLSL shaders dynamically from effect XML CDATA.
- Evaluates GL uniforms with real-time layer dimensions (`u_resolution`, `u_texture`, parameters, colors).
- `renderEffect(Bitmap, EffectDefinition)` / `applyEffect(...)`: Renders pixel-perfect shader passes with bottom-up FBO alignment (`y * width` buffer copy), eliminating vertical flipping bugs.
- `buildFragmentShaderCode(EffectDefinition)`: Prepares GLSL shader code by injecting precision specifiers, texture sampler structs (`TextureInfo`), math helpers (`texture2DCv`, `saturate`, `lerp`), and safe division guards (`max(compColor.a, 0.0001)`).

### [`EffectPipeline.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/effect/EffectPipeline.java)
Chains multiple applied effects sequentially on layer bitmaps with caching.
- `processLayerEffects(CanvasLayer layer, Bitmap source, RectF layerBounds, RectF canvasBounds)`: Runs input bitmap through each enabled `EffectDefinition` using `GLEffectEngine`, backed by an LRU bitmap cache.
- `applyMaskAndStyling(Canvas, ...)` / `applyPostDraw(Canvas, ...)`: Handles procedural masks, trims, shadow styles, and post-draw overlays.

### [`EffectHelper.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/effect/EffectHelper.java)
Loads and parses 300+ effect XML definitions from `assets/effects/`.
- Excludes video/timeline/animation-only effects (e.g. `flicker.xml`, `oscillate.xml`, `drawing-progress.xml`).
- Categorizes effects into Alight Motion groups: *Blur*, *Color & Light*, *Drawing & Edge*, *3D*, *Distortion / Warp*, *Procedural*, *Matte / Mask / Key*, *Repeat*, *Text*.
- `loadAllEffects(Context)`: Parses XML files into cached `EffectDefinition` objects.
- `parseEffectXml(InputStream)`: Extracts `<params>`, uniforms, pass configurations (`<pass target="..." effect="..." />`), and `<shader type="fragment"><![CDATA[ ... ]]></shader>`.
- `getEffectsByCategory(category)` / `searchEffects(query)`: Query helpers for effect browser.

### [`EffectDefinition.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/effect/EffectDefinition.java)
Data model for a single effect.
- Holds `id`, `name`, `category`, `description`, `thumbPath`, `params`, `shaderSource`, `passTargets`, `passEffects`, and `uniformTypes`.
- `copy()`: Deep-clones effect definitions when added to layers.

### [`EffectParam.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/effect/EffectParam.java)
Data model for a single effect parameter.
- Supports types: `SLIDER`, `SPINNER`, `SWITCH`, `COLOR`, `POINT`, `SELECTOR`, `XYZ`.
- Stores `min`, `max`, `defaultValue`, `step`, and current typed value (`floatValue`, `colorValue`, `boolValue`, etc.).

### [`EffectControlHelper.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/effect/EffectControlHelper.java)
Dynamically constructs Material UI control cards (sliders, color buttons, toggles) in the effect panel for an active effect's declared parameters.

---

## 5. Shape Engine & Geometry (`glab.pixeleditor.shape`)

### [`ShapeHelper.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/shape/ShapeHelper.java)
Loads procedural shape definitions from `assets/shapes/*.xml`.
- `loadAllShapes(Context)`: Parses shape XML files, procedural parameters, and path formulas.
- `getShapeById(Context, id)`: Returns specific shape template.

### [`ShapeDefinition.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/shape/ShapeDefinition.java)
Model for vector shape templates, containing custom procedural parameters (`points`, `innerRadius`, `depth`, `curve`, `roundness`).

### [`ShapeGeometryHelper.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/shape/ShapeGeometryHelper.java)
Computes Android `android.graphics.Path` geometry for all 20 XML vector shapes from `assets/shapes/`:
- Stars (pointCount, outerRadius, innerRadius, offsetAngle).
- Polygons / Pentagons / Triangles (sideCount, radius, offsetAngle).
- Curved geometry (teardrop, crescent moon, rounded rectangle, plus/cross, arrows, pie/arc, multifoil petals, stamp badge, speech callouts, wide line).
- `renderShapeThumbnail(ShapeDefinition, sizePx, color)`: Generates crisp vector previews for the Add Element sheet and shape selector dialogs.

---

## 6. UI Dialogs & Navigation (`glab.pixeleditor.ui`)

### [`AlightColorPickerView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/AlightColorPickerView.java) & [`AlightColorPickerDialog.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/AlightColorPickerDialog.java)
Full-featured color selector inspired by Alight Motion:
- Mode tabs: *Palette Swatches*, *Spectrum 2D Plane*, *RGB/HSV Sliders* (using `ClampedSliderView`), *Eyedropper Loupe*.
- Real-time color preview and hex code input.

### [`ColorSpectrumPlaneView.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/ColorSpectrumPlaneView.java)
2D Saturation / Value spectrum canvas with draggable selector thumb and crosshairs.

### [`HomeActivity.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/HomeActivity.java)
Home project dashboard displaying recent projects in a responsive grid with search, sort, and deletion.

### [`CreateProjectBottomSheet.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/CreateProjectBottomSheet.java)
Bottom sheet for creating new projects with preset aspect ratios (1:1, 9:16, 16:9, 4:5, 4:3), custom resolutions, and background colors.
- `calculateAspectRatio(width, height)`: Dynamically computes GCD-reduced aspect ratios for arbitrary custom dimensions.

### [`ProjectAdapter.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/ProjectAdapter.java)
RecyclerView adapter for rendering project cards with thumbnail previews.

### [`CanvasLayerSidebarAdapter.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/CanvasLayerSidebarAdapter.java)
RecyclerView adapter for layer management (visibility toggle, lock toggle, Z-order reordering, and active selection).

### [`SplashActivity.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/ui/SplashActivity.java)
Splash launch screen transitioning to `HomeActivity`.

---

## 7. Assets & Resources Reference

- `assets/effects/*.xml`: 300+ Alight Motion GLSL effect definitions (`boxblur3.xml`, `chromakey.xml`, `innerblur.xml`, `vortexblur.xml`, etc.).
- `assets/shapes/*.xml`: Procedural shape XML templates (`star.xml`, `roundrect.xml`, `poly.xml`, `teardrop.xml`, etc.).
- `res/layout/activity_main.xml`: Root editor layout with expandable top bar, center canvas, floating tools, and docked bottom panel flipper.
- `res/layout/panel_*.xml`: Subpanels for Color/Fill, Border/Shadow, Move/Transform, Shape Edit, Photo Adjust, Effect Controls, and Layer Overview.

---

## 8. Dynamic ViewFlipper & Add Element Sheet Fixes

### [`AutoHeightViewFlipper.java`](file:///c:/Users/sddrk/AndroidStudioProjects/MyApplication/app/src/main/java/glab/pixeleditor/view/AutoHeightViewFlipper.java)
- Extends `ViewFlipper` and overrides `onMeasure` to measure only the currently active displayed child (`getChildAt(getDisplayedChild())`) with margins and padding instead of taking the maximum height across all 11 subpanels.
- Solves the bottom gap / empty void beneath `panel_layer_menu` and compact subpanels, providing optimal canvas area and clean docked bottom sheet appearance.

### Add Element Sheet & SVG Icons Integration (`MainActivity.java`, `sheet_add_element.xml`)
- Bound `tabAddIcons` (`@+id/tabAddIcons`) to `showIconsGrid.run()` which loads SVG icons via `SvgIconManager.getAllIcons()` and renders crisp previews using `SvgIconAdapter`.
- Removed accidental listener overwrite that opened canvas settings when clicking the Icons tab.
- Connected `btnCloseAddSheet` to intelligently return to `PANEL_LAYERS_OVERVIEW` (or `PANEL_LAYER_MENU` if a layer is actively selected).
- Added live search bar (`@+id/layoutIconSearchBar`, `etIconSearch`, `btnClearIconSearch`) in `sheet_add_element.xml` and wired real-time filtering in `SvgIconAdapter.java` (`filter(query)`).
- Applied edge-to-edge system navigation and IME keyboard window insets (`WindowInsetsCompat.Type.ime()`) to `binding.layoutBottomContainer` and `scrollEffectBrowser`, ensuring panels, search inputs, and icon grids lift automatically above the keyboard when typing with zero overlap.

---

## 9. Recent Enhancements & Design System Polish

### Tabler Icons Migration (`res/drawable/*.xml`, `assets/svg.zip`)
- Replaced 81 vector drawable icons across toolbars, dialogs, transform controls, edit panels, layer stack, and shape previews with crisp, uniform Tabler vector icons (`24x24dp`, `strokeWidth=2dp`, `round` caps and joins) directly from `assets/svg.zip`.
- Outlined vector drawables cleanly inherit tint colors (`#00E5BC`, `#94A3B8`, `#FFFFFF`) without distortion or clipping.

### Edit Text Panel — Favorites Empty State & Chip Styling (`panel_edit_text.xml`, `MainActivity.java`)
- Added dedicated `layoutEmptyFonts` empty state view containing a star icon, title (*"No favorite fonts yet"*), and helper subtitle (*"Tap the star icon next to any font to add it to your favorites."*).
- Preserves the user's active "Favorites" tab selection instead of forcibly falling back to "All" fonts when favorites is empty.
- Toggling favorite status on fonts dynamically updates the list and smoothly toggles between `rvFontsList` and `layoutEmptyFonts`.
- Fixed chip text contrast:
  - **Selected Chip State**: Dark text (`#00382B` / `0xFF00382B`) over mint pill background (`R.drawable.bg_pill_accent` `#00E5BC`) for readable contrast.
  - **Unselected Chip State**: Muted slate text (`#94A3B8` / `0xFF94A3B8`) over dark pill backgrounds (`R.drawable.bg_chip_pill` / `R.drawable.bg_input_box`).

### Effect Controls — Slider Mistouch Protection (`ScrubRulerView.java`, `EffectControlHelper.java`)
- Added `isActive` state and touch filtering to `ScrubRulerView`:
  - When **active** (`isActive = true`, full opacity `alpha = 1.0f`): ruler captures horizontal touch gestures (`requestDisallowInterceptTouchEvent(true)`), changes effect parameter values, and produces haptic feedback.
  - When **inactive** (`isActive = false`, dimmed opacity `alpha = 0.45f`): ruler lets parent `ScrollView` scroll vertically without capturing touches, preventing accidental parameter modifications during scrolling.
  - Tapping an inactive ruler or its parameter label immediately focuses and activates that parameter.



