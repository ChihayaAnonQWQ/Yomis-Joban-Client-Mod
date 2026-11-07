package com.jsblock.amberui.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Three coordinate fields laid out as one row (X, Y, Z).
 */
public class BlockPosWidget extends AbstractWidget implements WidgetsWrapper {
	private final CoordTextField posXTextField;
	private final CoordTextField posYTextField;
	private final CoordTextField posZTextField;
	private final List<AbstractWidget> children;

	public BlockPosWidget(int x, int y, int width, int height) {
		super(x, y, width, height, Component.empty());
		this.posXTextField = new CoordTextField(0, 0, width / 3, height, -29999999, 29999999, 0);
		this.posYTextField = new CoordTextField(width / 3, 0, width / 3, height, -29999999, 29999999, 0);
		this.posZTextField = new CoordTextField((width / 3) * 2, 0, width / 3, height, -29999999, 29999999, 0);
		this.children = new ArrayList<>(Arrays.asList(posXTextField, posYTextField, posZTextField));
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		this.posXTextField.render(guiGraphics, mouseX, mouseY, delta);
		this.posYTextField.render(guiGraphics, mouseX, mouseY, delta);
		this.posZTextField.render(guiGraphics, mouseX, mouseY, delta);
	}

	@Override
	public void setAllX(int newX) {
		setX(newX);
		positionWidgets();
	}

	@Override
	public void setAllY(int newY) {
		setY(newY);
		positionWidgets();
	}

	public void positionWidgets() {
		final int perWidth = getWidth() / 3;
		this.posXTextField.setX(getX());
		this.posYTextField.setX(getX() + perWidth);
		this.posZTextField.setX(getX() + perWidth + perWidth);
		this.posXTextField.setY(getY());
		this.posYTextField.setY(getY());
		this.posZTextField.setY(getY());
		this.posXTextField.setWidth(perWidth);
		this.posYTextField.setWidth(perWidth);
		this.posZTextField.setWidth(perWidth);
	}

	public void setBlockPos(BlockPos newPos) {
		this.posXTextField.setValue(newPos.getX());
		this.posYTextField.setValue(newPos.getY());
		this.posZTextField.setValue(newPos.getZ());
	}

	public BlockPos getBlockPos() {
		return new BlockPos(posXTextField.getNumber(), posYTextField.getNumber(), posZTextField.getNumber());
	}

	@Override
	public List<AbstractWidget> getChildWidgets() {
		return children;
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
	}
}
