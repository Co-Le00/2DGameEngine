package ge;

import editor.GameViewWindow;
import editor.MenuBar;
import editor.PropertiesWindow;
import editor.SceneHierarchyWindow;
import imgui.*;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import renderer.PickingTexture;
import scenes.Scene;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

public class ImGuiLayer {

    private long glfwWindow;


    // LWJGL3 renderer (SHOULD be initialized)
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();

    private GameViewWindow gameViewWindow;
    private PropertiesWindow propertiesWindow;
    private MenuBar menuBar;
    private SceneHierarchyWindow sceneHeirarchyWindow;

    public ImGuiLayer(long glfwWindow, PickingTexture pickingTexture) {
        this.glfwWindow = glfwWindow;
        this.gameViewWindow = new GameViewWindow();
        this.propertiesWindow = new PropertiesWindow(pickingTexture);
        this.menuBar = new MenuBar();
        this.sceneHeirarchyWindow = new SceneHierarchyWindow();
    }

    public GameViewWindow getGameViewWindow() {
        return this.gameViewWindow;
    }

    // Initialize Dear ImGui.
    public void initImGui() {
        // IMPORTANT!!
        // This line is critical for Dear ImGui to work.
        ImGui.createContext();

        // ------------------------------------------------------------
        // Initialize ImGuiIO config
        final ImGuiIO io = ImGui.getIO();

        io.setIniFilename("imgui.ini"); // We don't want to save .ini file
        io.setConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        io.setBackendPlatformName("imgui_java_impl_glfw");


        // ------------------------------------------------------------
        // GLFW callbacks to handle user input

        glfwSetKeyCallback(glfwWindow, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                io.setKeysDown(key, true);
            } else if (action == GLFW_RELEASE) {
                io.setKeysDown(key, false);
            }

            io.setKeyCtrl(io.getKeysDown(GLFW_KEY_LEFT_CONTROL) || io.getKeysDown(GLFW_KEY_RIGHT_CONTROL));
            io.setKeyShift(io.getKeysDown(GLFW_KEY_LEFT_SHIFT) || io.getKeysDown(GLFW_KEY_RIGHT_SHIFT));
            io.setKeyAlt(io.getKeysDown(GLFW_KEY_LEFT_ALT) || io.getKeysDown(GLFW_KEY_RIGHT_ALT));
            io.setKeySuper(io.getKeysDown(GLFW_KEY_LEFT_SUPER) || io.getKeysDown(GLFW_KEY_RIGHT_SUPER));

            if (!io.getWantCaptureKeyboard()) {
                KeyListener.keyCallback(w, key, scancode, action, mods);
            }
        });

        glfwSetCharCallback(glfwWindow, (w, c) -> {
            if (c != GLFW_KEY_DELETE) {
                io.addInputCharacter(c);
            }
        });

        glfwSetMouseButtonCallback(glfwWindow, (w, button, action, mods) -> {
            final boolean[] mouseDown = new boolean[5];

            mouseDown[0] = button == GLFW_MOUSE_BUTTON_1 && action != GLFW_RELEASE;
            mouseDown[1] = button == GLFW_MOUSE_BUTTON_2 && action != GLFW_RELEASE;
            mouseDown[2] = button == GLFW_MOUSE_BUTTON_3 && action != GLFW_RELEASE;
            mouseDown[3] = button == GLFW_MOUSE_BUTTON_4 && action != GLFW_RELEASE;
            mouseDown[4] = button == GLFW_MOUSE_BUTTON_5 && action != GLFW_RELEASE;

            io.setMouseDown(mouseDown);

            if (!io.getWantCaptureMouse() && mouseDown[1]) {
                ImGui.setWindowFocus(null);
            }

            if (!io.getWantCaptureMouse() || gameViewWindow.getWantCaptureMouse()) {
                MouseListener.mouseButtonCallback(w, button, action, mods);

            }
        });

        glfwSetScrollCallback(glfwWindow, (w, xOffset, yOffset) -> {
            io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
            io.setMouseWheel(io.getMouseWheel() + (float) yOffset);
            if (!io.getWantCaptureMouse() || gameViewWindow.getWantCaptureMouse()) {
                MouseListener.mouseScrollCallback(w, xOffset, yOffset);
            } else {
                MouseListener.clear();
            }
        });

        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(final String s) {
                glfwSetClipboardString(glfwWindow, s);
            }
        });

        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                final String clipboardString = glfwGetClipboardString(glfwWindow);
                if (clipboardString != null) {
                    return clipboardString;
                } else {
                    return "";
                }
            }
        });

        // ------------------------------------------------------------
        // Fonts configuration
        // Read: https://raw.githubusercontent.com/ocornut/imgui/master/docs/FONTS.txt

        final ImFontAtlas fontAtlas = io.getFonts();
        final ImFontConfig fontConfig = new ImFontConfig(); // Natively allocated object, should be explicitly destroyed

        // Glyphs could be added per-font as well as per config used globally like here
        fontConfig.setGlyphRanges(fontAtlas.getGlyphRangesDefault());



        // Fonts merge example
        fontConfig.setPixelSnapH(true);
        fontAtlas.addFontFromFileTTF("assets/fonts/segoeui.ttf", 20, fontConfig);

        fontConfig.destroy(); // After all fonts were added we don't need this config more


        // Method initializes LWJGL3 renderer.
        // This method SHOULD be called after you've initialized your ImGui configuration (fonts and so on).
        // ImGui context should be created as well.
        imGuiGlfw.init(glfwWindow, false);
        imGuiGl3.init("#version 330 core");

        initializeImGuiStyle();
    }

    public void update(float dt, Scene currentScene) {

        startFrame(dt);

        // Any Dear ImGui code SHOULD go between ImGui.newFrame()/ImGui.render() methods
        setupDockspace();
        currentScene.imgui();
        //ImGui.showDemoWindow();
        gameViewWindow.imgui();
        propertiesWindow.imgui();
        sceneHeirarchyWindow.imgui();

        endFrame();
    }

    private void startFrame(final float deltaTime) {
        imGuiGlfw.newFrame();
        ImGui.newFrame();
    }

    private void endFrame() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, Window.getWidth(), Window.getHeight());
        System.out.println(Window.getWidth());
        System.out.println(Window.getHeight());
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);

        // After Dear ImGui prepared a draw data, we use it in the LWJGL3 renderer.
        // At that moment ImGui will be rendered to the current OpenGL context.
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

       long backupWindowPtr = glfwGetCurrentContext();
       ImGui.updatePlatformWindows();
       ImGui.renderPlatformWindowsDefault();
       glfwMakeContextCurrent(backupWindowPtr);
    }

    // If you want to clean a room after yourself - do it by yourself
    private void destroyImGui() {
        imGuiGl3.dispose();
        ImGui.destroyContext();
    }

    private void setupDockspace() {
        int windowFlags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoDocking;

        ImGuiViewport mainViewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(mainViewport.getWorkPosX(), mainViewport.getWorkPosY());
        ImGui.setNextWindowSize(mainViewport.getWorkSizeX(), mainViewport.getWorkSizeY());
        ImGui.setNextWindowViewport(mainViewport.getID());
        ImGui.setNextWindowPos(0.0f, 0.0f);
        ImGui.setNextWindowSize(Window.getWidth(), Window.getHeight());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        windowFlags |= ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse |
                       ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove |
                       ImGuiWindowFlags.NoBringToFrontOnFocus |ImGuiWindowFlags.NoNavFocus;

        ImGui.begin("Dockspace Demo", new ImBoolean(true), windowFlags);
        ImGui.popStyleVar(2);

        //Dockspace
        ImGui.dockSpace(ImGui.getID("Dockspace"));

        menuBar.imgui();

        ImGui.end();
    }

    public PropertiesWindow getPropertiesWindow() {
        return this.propertiesWindow;
    }

    private void initializeImGuiStyle() {
        ImGuiStyle style = ImGui.getStyle();
        float[][] colors = new float[ImGuiCol.COUNT][4];
        style.getColors(colors);

        colors[ImGuiCol.Text]                   = new float[]{1.000f, 1.000f, 1.000f, 1.000f};
        colors[ImGuiCol.TextDisabled]           = new float[]{0.500f, 0.500f, 0.500f, 1.000f};
        colors[ImGuiCol.WindowBg]               = new float[]{0.180f, 0.180f, 0.180f, 1.000f};
        colors[ImGuiCol.ChildBg]                = new float[]{0.280f, 0.280f, 0.280f, 0.000f};
        colors[ImGuiCol.PopupBg]                =new float[]{0.313f, 0.313f, 0.313f, 1.000f};
        colors[ImGuiCol.Border]                 = new float[]{0.266f, 0.266f, 0.266f, 1.000f};
        colors[ImGuiCol.BorderShadow]           = new float[]{0.000f, 0.000f, 0.000f, 0.000f};
        colors[ImGuiCol.FrameBg]                = new float[]{0.160f, 0.160f, 0.160f, 1.000f};
        colors[ImGuiCol.FrameBgHovered]         = new float[]{0.200f, 0.200f, 0.200f, 1.000f};
        colors[ImGuiCol.FrameBgActive]          = new float[]{0.280f, 0.280f, 0.280f, 1.000f};
        colors[ImGuiCol.TitleBg]                = new float[]{0.148f, 0.148f, 0.148f, 1.000f};
        colors[ImGuiCol.TitleBgActive]          = new float[]{0.148f, 0.148f, 0.148f, 1.000f};
        colors[ImGuiCol.TitleBgCollapsed]       = new float[]{0.148f, 0.148f, 0.148f, 1.000f};
        colors[ImGuiCol.MenuBarBg]              = new float[]{0.195f, 0.195f, 0.195f, 1.000f};
        colors[ImGuiCol.ScrollbarBg]            = new float[]{0.160f, 0.160f, 0.160f, 1.000f};
        colors[ImGuiCol.ScrollbarGrab]          = new float[]{0.277f, 0.277f, 0.277f, 1.000f};
        colors[ImGuiCol.ScrollbarGrabHovered]   = new float[]{0.300f, 0.300f, 0.300f, 1.000f};
        colors[ImGuiCol.ScrollbarGrabActive]    = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.CheckMark]              = new float[]{1.000f, 1.000f, 1.000f, 1.000f};
        colors[ImGuiCol.SliderGrab]             = new float[]{0.391f, 0.391f, 0.391f, 1.000f};
        colors[ImGuiCol.SliderGrabActive]       = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.Button]                 = new float[]{1.000f, 1.000f, 1.000f, 0.000f};
        colors[ImGuiCol.ButtonHovered]          = new float[]{1.000f, 1.000f, 1.000f, 0.156f};
        colors[ImGuiCol.ButtonActive]           = new float[]{1.000f, 1.000f, 1.000f, 0.391f};
        colors[ImGuiCol.Header]                 = new float[]{0.313f, 0.313f, 0.313f, 1.000f};
        colors[ImGuiCol.HeaderHovered]          = new float[]{0.469f, 0.469f, 0.469f, 1.000f};
        colors[ImGuiCol.HeaderActive]           = new float[]{0.469f, 0.469f, 0.469f, 1.000f};
        colors[ImGuiCol.Separator]              = colors[ImGuiCol.Border];
        colors[ImGuiCol.SeparatorHovered]       = new float[]{0.391f, 0.391f, 0.391f, 1.000f};
        colors[ImGuiCol.SeparatorActive]        = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.ResizeGrip]             = new float[]{1.000f, 1.000f, 1.000f, 0.250f};
        colors[ImGuiCol.ResizeGripHovered]      = new float[]{1.000f, 1.000f, 1.000f, 0.670f};
        colors[ImGuiCol.ResizeGripActive]       = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.Tab]                    = new float[]{0.098f, 0.098f, 0.098f, 1.000f};
        colors[ImGuiCol.TabHovered]             = new float[]{0.352f, 0.352f, 0.352f, 1.000f};
        colors[ImGuiCol.TabActive]              = new float[]{0.195f, 0.195f, 0.195f, 1.000f};
        colors[ImGuiCol.TabUnfocused]           = new float[]{0.098f, 0.098f, 0.098f, 1.000f};
        colors[ImGuiCol.TabUnfocusedActive]     = new float[]{0.195f, 0.195f, 0.195f, 1.000f};
        colors[ImGuiCol.DockingPreview]         = new float[]{1.000f, 0.391f, 0.000f, 0.781f};
        colors[ImGuiCol.DockingEmptyBg]         = new float[]{0.180f, 0.180f, 0.180f, 1.000f};
        colors[ImGuiCol.PlotLines]              = new float[]{0.469f, 0.469f, 0.469f, 1.000f};
        colors[ImGuiCol.PlotLinesHovered]       = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.PlotHistogram]          = new float[]{0.586f, 0.586f, 0.586f, 1.000f};
        colors[ImGuiCol.PlotHistogramHovered]   = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.TextSelectedBg]         = new float[]{1.000f, 1.000f, 1.000f, 0.156f};
        colors[ImGuiCol.DragDropTarget]         = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.NavHighlight]           = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.NavWindowingHighlight]  = new float[]{1.000f, 0.391f, 0.000f, 1.000f};
        colors[ImGuiCol.NavWindowingDimBg]      = new float[]{0.000f, 0.000f, 0.000f, 0.586f};
        colors[ImGuiCol.ModalWindowDimBg]       = new float[]{0.000f, 0.000f, 0.000f, 0.586f};


        style.setColors(colors);
        style.setChildRounding(4.0f);
        style.setFrameBorderSize(1.0f);
        style.setFrameRounding(0.0f);
        style.setGrabMinSize(7.0f);
        style.setPopupRounding(0.0f);
        style.setScrollbarRounding(12.0f);
        style.setScrollbarSize(13.0f);
        style.setTabBorderSize(1.0f);
        style.setTabRounding(0.0f);
        style.setWindowRounding(0.0f);
    }
}
