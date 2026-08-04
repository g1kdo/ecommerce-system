package rw.smart.ecommerce.utils.ui;

/**
 * Implemented by screens that the shell keeps alive between visits. The shell
 * caches each view so in-progress state survives navigation (most importantly the
 * shop cart) and calls {@link #onShown()} on re-entry so the data is still fresh.
 */
public interface RefreshableView {

    void onShown();
}
