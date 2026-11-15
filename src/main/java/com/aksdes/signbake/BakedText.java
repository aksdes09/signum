package com.aksdes.signbake;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.world.level.block.entity.SignText;

/**
 * What SignBake knows about one side of one sign.
 */
public final class BakedText {

	/** The text sits in an atlas cell and is drawn as one quad. */
	public static final byte QUAD = 0;
	/** Nothing visible on this side: nothing is drawn at all. */
	public static final byte EMPTY = 1;
	/** Left to the vanilla renderer (obfuscated text, private-use glyphs, oversized signs). */
	public static final byte VANILLA = 2;

	final SignText owner;

	public byte kind;
	public boolean filtered;
	public boolean glowing;
	public boolean outline;
	public int generation;

	/** Atlas page of the cell, or null when this text owns no cell. */
	public Page page;
	int cell;
	int liveIndex;

	public int lastUsed;
	public int light;

	/** Centre of the cell in atlas units: the origin the sign text is drawn around. */
	public float centerX;
	public float centerY;
	private float halfWidth;
	private float halfHeight;
	private float u0;
	private float u1;
	private float vTop;
	private float vBottom;

	/** Created once per text, so drawing a sign allocates nothing per frame. */
	public final SubmitNodeCollector.CustomGeometryRenderer renderer = this::emit;

	public BakedText(final SignText owner) {
		this.owner = owner;
	}

	public boolean isStale(final SignRenderState state) {
		return this.generation != SignBake.generation
				|| this.filtered != state.isTextFilteringEnabled
				|| (this.glowing && this.outline != state.drawOutline);
	}

	void place(final int cell, final int maxLineWidth, final int lineHeight) {
		this.cell = cell;
		this.centerX = (cell % SignBake.COLUMNS) * SignBake.CELL_WIDTH + SignBake.CELL_WIDTH / 2.0F;
		this.centerY = (cell / SignBake.COLUMNS) * SignBake.CELL_HEIGHT + SignBake.CELL_HEIGHT / 2.0F;
		// The text area plus a few units for glow outlines and bold/italic overhang.
		this.halfWidth = Math.min(SignBake.CELL_WIDTH, maxLineWidth + 6) / 2.0F;
		this.halfHeight = Math.min(SignBake.CELL_HEIGHT, lineHeight * 4 + 6) / 2.0F;
		this.u0 = (this.centerX - this.halfWidth) / SignBake.PAGE_UNITS;
		this.u1 = (this.centerX + this.halfWidth) / SignBake.PAGE_UNITS;
		this.vTop = 1.0F - (this.centerY - this.halfHeight) / SignBake.PAGE_UNITS;
		this.vBottom = 1.0F - (this.centerY + this.halfHeight) / SignBake.PAGE_UNITS;
	}

	private void emit(final PoseStack.Pose pose, final VertexConsumer out) {
		final float w = this.halfWidth;
		final float h = this.halfHeight;
		final int light = this.light;
		out.addVertex(pose, -w, h, 0.0F).setColor(255, 255, 255, 255).setUv(this.u0, this.vBottom).setLight(light);
		out.addVertex(pose, w, h, 0.0F).setColor(255, 255, 255, 255).setUv(this.u1, this.vBottom).setLight(light);
		out.addVertex(pose, w, -h, 0.0F).setColor(255, 255, 255, 255).setUv(this.u1, this.vTop).setLight(light);
		out.addVertex(pose, -w, -h, 0.0F).setColor(255, 255, 255, 255).setUv(this.u0, this.vTop).setLight(light);
	}

}
