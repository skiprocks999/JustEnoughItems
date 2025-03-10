package mezz.jei.gui.overlay.bookmarks;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IClickableIngredientInternal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.input.IDragHandler;
import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.input.handlers.CheatInputHandler;
import mezz.jei.gui.input.handlers.CombinedInputHandler;
import mezz.jei.gui.input.handlers.NullDragHandler;
import mezz.jei.gui.input.handlers.ProxyDragHandler;
import mezz.jei.gui.input.handlers.ProxyInputHandler;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ScreenPropertiesCache;
import mezz.jei.gui.util.CheatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class BookmarkOverlay implements IRecipeFocusSource, IBookmarkOverlay {
	private static final int BORDER_MARGIN = 6;
	private static final int INNER_PADDING = 2;
	private static final int BUTTON_SIZE = 20;

	// input
	private final CheatInputHandler cheatInputHandler;

	// areas
	private final ScreenPropertiesCache screenPropertiesCache;

	// display elements
	private final IngredientGridWithNavigation contents;
	private final GuiIconToggleButton bookmarkButton;

	// data
	private final BookmarkList bookmarkList;
	private final IClientToggleState toggleState;

	public BookmarkOverlay(
		BookmarkList bookmarkList,
		Textures textures,
		IngredientGridWithNavigation contents,
		IClientConfig clientConfig,
		IClientToggleState toggleState,
		IScreenHelper screenHelper,
		IConnectionToServer serverConnection,
		IInternalKeyMappings keyBindings,
		CheatUtil cheatUtil
	) {
		this.bookmarkList = bookmarkList;
		this.toggleState = toggleState;
		this.bookmarkButton = BookmarkButton.create(this, bookmarkList, textures, toggleState, keyBindings);
		this.cheatInputHandler = new CheatInputHandler(this, toggleState, clientConfig, serverConnection, cheatUtil);
		this.contents = contents;
		this.screenPropertiesCache = new ScreenPropertiesCache(screenHelper);
		bookmarkList.addSourceListChangedListener(() -> {
			toggleState.setBookmarkEnabled(!bookmarkList.isEmpty());
			Minecraft minecraft = Minecraft.getInstance();
			Screen screen = minecraft.screen;
			this.updateScreen(screen, null);
		});
	}

	public boolean isListDisplayed() {
		return toggleState.isBookmarkOverlayEnabled() &&
			screenPropertiesCache.hasValidScreen() &&
			contents.hasRoom() &&
			!bookmarkList.isEmpty();
	}

	public boolean hasRoom() {
		return contents.hasRoom();
	}

	public void updateScreen(@Nullable Screen guiScreen, @Nullable Set<ImmutableRect2i> updatedGuiExclusionAreas) {
		this.screenPropertiesCache.updateScreen(guiScreen, updatedGuiExclusionAreas, this::onScreenPropertiesChanged);
	}

	private void onScreenPropertiesChanged() {
		this.screenPropertiesCache.getGuiProperties()
			.ifPresentOrElse(guiProperties -> {
				Set<ImmutableRect2i> guiExclusionAreas = this.screenPropertiesCache.getGuiExclusionAreas();
				updateBounds(guiProperties, guiExclusionAreas);
			}, this.contents::close);
	}

	private void updateBounds(IGuiProperties guiProperties, Set<ImmutableRect2i> guiExclusionAreas) {
		ImmutableRect2i displayArea = getDisplayArea(guiProperties);

		ImmutableRect2i availableContentsArea = displayArea.cropBottom(BUTTON_SIZE + INNER_PADDING);
		this.contents.updateBounds(availableContentsArea, guiExclusionAreas);
		this.contents.updateLayout(false);

		if (contents.hasRoom()) {
			ImmutableRect2i contentsArea = this.contents.getBackgroundArea();
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.matchWidthAndX(contentsArea)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
		} else {
			ImmutableRect2i bookmarkButtonArea = displayArea
				.insetBy(BORDER_MARGIN)
				.keepBottom(BUTTON_SIZE)
				.keepLeft(BUTTON_SIZE);
			this.bookmarkButton.updateBounds(bookmarkButtonArea);
		}
	}

	private static ImmutableRect2i getDisplayArea(IGuiProperties guiProperties) {
		int width = guiProperties.getGuiLeft();
		if (width <= 0) {
			width = 0;
		}
		int screenHeight = guiProperties.getScreenHeight();
		return new ImmutableRect2i(0, 0, width, screenHeight);
	}

	public void drawScreen(Minecraft minecraft, PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
		if (isListDisplayed()) {
			this.contents.draw(minecraft, poseStack, mouseX, mouseY, partialTicks);
		}
		if (this.screenPropertiesCache.hasValidScreen()) {
			this.bookmarkButton.draw(poseStack, mouseX, mouseY, partialTicks);
		}
	}

	public void drawTooltips(Minecraft minecraft, PoseStack poseStack, int mouseX, int mouseY) {
		if (isListDisplayed()) {
			this.contents.drawTooltips(minecraft, poseStack, mouseX, mouseY);
		}
		if (this.screenPropertiesCache.hasValidScreen()) {
			bookmarkButton.drawTooltips(poseStack, mouseX, mouseY);
		}
	}

	@Override
	public Stream<IClickableIngredientInternal<?>> getIngredientUnderMouse(double mouseX, double mouseY) {
		if (isListDisplayed()) {
			return this.contents.getIngredientUnderMouse(mouseX, mouseY);
		}
		return Stream.empty();
	}

	@Override
	public Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
		double mouseX = MouseUtil.getX();
		double mouseY = MouseUtil.getY();
		return getIngredientUnderMouse(mouseX, mouseY)
			.<ITypedIngredient<?>>map(IClickableIngredientInternal::getTypedIngredient)
			.findFirst();
	}

	@Nullable
	@Override
	public <T> T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		double mouseX = MouseUtil.getX();
		double mouseY = MouseUtil.getY();
		return getIngredientUnderMouse(mouseX, mouseY)
			.map(IClickableIngredientInternal::getTypedIngredient)
			.map(i -> i.getIngredient(ingredientType))
			.flatMap(Optional::stream)
			.findFirst()
			.orElse(null);
	}

	public IUserInputHandler createInputHandler() {
		final IUserInputHandler bookmarkButtonInputHandler = this.bookmarkButton.createInputHandler();

		final IUserInputHandler displayedInputHandler = new CombinedInputHandler(
			this.cheatInputHandler,
			this.contents.createInputHandler(),
			bookmarkButtonInputHandler
		);

		return new ProxyInputHandler(() -> {
			if (isListDisplayed()) {
				return displayedInputHandler;
			}
			return bookmarkButtonInputHandler;
		});
	}

	public IDragHandler createDragHandler() {
		final IDragHandler displayedDragHandler = this.contents.createDragHandler();

		return new ProxyDragHandler(() -> {
			if (isListDisplayed()) {
				return displayedDragHandler;
			}
			return NullDragHandler.INSTANCE;
		});
	}

	public void drawOnForeground(Minecraft minecraft, PoseStack poseStack, int mouseX, int mouseY) {
		if (isListDisplayed()) {
			this.contents.drawOnForeground(minecraft, poseStack, mouseX, mouseY);
		}
	}
}
