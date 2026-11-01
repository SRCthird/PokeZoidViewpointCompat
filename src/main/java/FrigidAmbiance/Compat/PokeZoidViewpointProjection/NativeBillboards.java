package FrigidAmbiance.Compat.PokeZoidViewpointProjection;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.core.textures.Texture;

public final class NativeBillboards {
    private NativeBillboards() {}
    private record Sprite(int id, float u0, float v0, float u1, float v1,
                          float x, float y, float w, float h, float ndcZ,
                          float viewportW, float viewportH, boolean xray) {}
    private static final Object LOCK = new Object();
    private static final List<Sprite> queued = new ArrayList<>();
    private static volatile boolean ready;
    private static volatile long lastSubmissionNanos;
    private static final long STALE_NANOS = 500_000_000L;

    public static void clear() {
        synchronized (LOCK) { queued.clear(); }
        lastSubmissionNanos = 0L;
    }
    private static int program, vao, vbo, sampler;
    private static boolean failed;

    public static final class Begin implements JavaFunction {
        public int call(LuaCallFrame frame, int count) {
            clear();
            return 0;
        }
    }
    public static final class Ready implements JavaFunction {
        public int call(LuaCallFrame frame, int count) {
            frame.push(ready && !failed);
            return 1;
        }
    }
    public static final class Submit implements JavaFunction {
        public int call(LuaCallFrame frame, int count) {
            if (count < 9 || !(frame.get(0) instanceof Texture tex)) return 0;
            try {
                float x = n(frame.get(1)), y = n(frame.get(2));
                float w = n(frame.get(3)), h = n(frame.get(4));
                float z = n(frame.get(5));
                float vw = n(frame.get(6)), vh = n(frame.get(7));
                boolean xray = Boolean.TRUE.equals(frame.get(8));
                if (w > 0 && h > 0 && vw > 0 && vh > 0 && Float.isFinite(z)) {
                    synchronized (LOCK) {
                        if (queued.size() < 256) {
                            queued.add(new Sprite(tex.getID(),
                             tex.getXStart(), tex.getYStart(), tex.getXEnd(), tex.getYEnd(),
                             x, y, w, h, z, vw, vh, xray));
                            lastSubmissionNanos = System.nanoTime();
                        }
                    }
                }
            } catch (Throwable ex) {
                System.out.println("[PokeZoidViewpoint] native submit failed: " + ex);
            }
            return 0;
        }
    }
    private static float n(Object v) { return ((Number)v).floatValue(); }

    public static void draw() {
        if (failed) return;
        List<Sprite> sprites;
        synchronized (LOCK) {
            if (lastSubmissionNanos == 0L ||
                System.nanoTime() - lastSubmissionNanos > STALE_NANOS) {
                queued.clear();
            }
            sprites = new ArrayList<>(queued);
        }
        try {
            if (program == 0) init();
            ready = true;
            if (sprites.isEmpty()) return;

            int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int oldArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int oldActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            try {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false);
                GL20.glUseProgram(program);
                GL30.glBindVertexArray(vao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GL20.glUniform1i(sampler, 0);
                GL11.glGetInteger(
                    GL30.GL_DRAW_FRAMEBUFFER_BINDING
                );
                GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
                );
                for (Sprite s : sprites) {
                    if (s.id <= 0 || s.ndcZ < -1 || s.ndcZ > 1) continue;
                    if (s.xray) GL11.glDisable(GL11.GL_DEPTH_TEST);
                    else { GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL); }
                    float left = 2f * s.x / s.viewportW - 1f;
                    float right = 2f * (s.x + s.w) / s.viewportW - 1f;
                    float top = 1f - 2f * s.y / s.viewportH;
                    float bottom = 1f - 2f * (s.y + s.h) / s.viewportH;
                    float[] verts = {
                        left, top, s.ndcZ, s.u0, s.v0,
                        right, top, s.ndcZ, s.u1, s.v0,
                        right, bottom, s.ndcZ, s.u1, s.v1,
                        left, top, s.ndcZ, s.u0, s.v0,
                        right, bottom, s.ndcZ, s.u1, s.v1,
                        left, bottom, s.ndcZ, s.u0, s.v1
                    };
                    FloatBuffer buffer = BufferUtils.createFloatBuffer(verts.length);
                    buffer.put(verts).flip();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, s.id);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
                }
            } finally {
                GL11.glDepthFunc(oldDepthFunc);
                GL11.glDepthMask(depthWrite);
                if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
                if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
                if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL20.glUseProgram(oldProgram);
                GL30.glBindVertexArray(oldVao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArrayBuffer);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
                GL13.glActiveTexture(oldActiveTex);
            }
        } catch (Throwable ex) {
            failed = true;
            ready = false;
            System.out.println("[PokeZoidViewpoint] native depth render disabled: " + ex);
            ex.printStackTrace(System.out);
        }
    }

    private static void init() {
        String vertex = "#version 330 core\n" +
            "layout(location=0) in vec3 position; layout(location=1) in vec2 texcoord;\n" +
            "out vec2 uv; void main(){uv=texcoord;gl_Position=vec4(position,1.0);}\n";
        String fragment = "#version 330 core\n" +
            "in vec2 uv; out vec4 color; uniform sampler2D tex0;\n" +
            "void main(){color=texture(tex0,uv);if(color.a<0.1) discard;}\n";
        int vs = shader(GL20.GL_VERTEX_SHADER, vertex);
        int fs = shader(GL20.GL_FRAGMENT_SHADER, fragment);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
            throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
        GL20.glDeleteShader(vs); GL20.glDeleteShader(fs);
        sampler = GL20.glGetUniformLocation(program, "tex0");
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        vao = GL30.glGenVertexArrays(); vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 12L);
        GL30.glBindVertexArray(previousVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
        System.out.println("[PokeZoidViewpoint] native depth billboard renderer initialized");
    }
    private static int shader(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source); GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0)
            throw new IllegalStateException(GL20.glGetShaderInfoLog(id));
        return id;
    }
}
