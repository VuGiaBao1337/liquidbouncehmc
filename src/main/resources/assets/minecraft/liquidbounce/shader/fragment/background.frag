/*
 * Blue Flag Background Shader
 * Modified for blue flag theme
 * Based on aurora borealis effect
 */
#version 120

#ifdef GL_ES
precision mediump float;
#endif

// Uniforms
uniform float iTime;
uniform vec2 iResolution;

// Hash functions
float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// Enhanced 2D noise
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    
    return mix(
        mix(hash2(i + vec2(0.0, 0.0)), hash2(i + vec2(1.0, 0.0)), u.x),
        mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), u.x), 
        u.y
    );
}

// Fractal noise
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p * frequency);
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

// Blue aurora layer
vec3 blueAuroraLayer(vec2 uv, float speed, float intensity, vec3 color, float offset) {
    float t = iTime * speed + offset;
    
    // Create flowing movement
    vec2 flow = vec2(
        sin(t * 0.3 + uv.x * 2.0) * 0.1,
        cos(t * 0.2 + uv.y * 1.5) * 0.05
    );
    
    vec2 p = uv * vec2(3.0, 2.0) + flow;
    
    // Multi-octave noise for aurora
    float n1 = noise(p + t * 0.5);
    float n2 = noise(p * 2.0 + t * 0.3) * 0.5;
    float n3 = noise(p * 4.0 + t * 0.1) * 0.25;
    
    float combined = n1 + n2 + n3;
    
    // Create aurora shape
    float auroraShape = smoothstep(0.2, 0.8, combined - uv.y * 0.7);
    auroraShape *= (1.0 - smoothstep(0.0, 0.4, combined - uv.y * 0.5));
    auroraShape *= smoothstep(0.0, 0.3, uv.y) * smoothstep(1.0, 0.7, uv.y);
    
    return auroraShape * intensity * color;
}

// Blue color palette for flag theme
vec3 getBlueColor(float t) {
    vec3 colors[5];
    colors[0] = vec3(0.1, 0.3, 0.8);  // Deep Blue
    colors[1] = vec3(0.2, 0.5, 1.0);  // Sky Blue  
    colors[2] = vec3(0.0, 0.4, 0.9);  // Royal Blue
    colors[3] = vec3(0.3, 0.6, 1.0);  // Light Blue
    colors[4] = vec3(0.1, 0.5, 0.9);  // Ocean Blue
    
    float index = t * 4.0;
    int i = int(index);
    float f = fract(index);
    
    if (i >= 4) return colors[4];
    return mix(colors[i], colors[i + 1], f);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    uv.x *= iResolution.x / iResolution.y;
    
    // Blue flag gradient - darker blue to lighter blue
    vec3 skyTop = vec3(0.05, 0.15, 0.4);     // Dark blue
    vec3 skyBottom = vec3(0.1, 0.3, 0.7);    // Medium blue
    vec3 skyColor = mix(skyBottom, skyTop, smoothstep(0.0, 1.0, uv.y));
    
    // Add subtle stars with blue tint
    float stars = 0.0;
    for (int i = 0; i < 3; i++) {
        vec2 starUV = uv * (50.0 + float(i) * 30.0);
        stars += smoothstep(0.98, 1.0, hash2(floor(starUV))) * 0.1;
    }
    skyColor += stars * vec3(0.7, 0.8, 1.0);
    
    // Create blue aurora layers
    vec3 auroraColor = vec3(0.0);
    float timeOffset = iTime * 0.1;
    
    for (int i = 0; i < 4; i++) {
        float layerTime = timeOffset + float(i) * 0.5;
        vec3 color = getBlueColor(fract(layerTime));
        float speed = 0.05 + float(i) * 0.02;
        float intensity = 0.4 - float(i) * 0.08;
        
        auroraColor += blueAuroraLayer(uv, speed, intensity, color, float(i) * 10.0);
    }
    
    // Final blue composition
    vec3 finalColor = skyColor + auroraColor;
    
    // Enhance blue tones
    finalColor.b *= 1.2; // Boost blue channel
    finalColor = pow(finalColor, vec3(0.9)); // Gamma correction
    finalColor *= 1.1; // Brightness boost
    
    fragColor = vec4(finalColor, 1.0);
}

void main() {
    mainImage(gl_FragColor, gl_FragCoord.xy);
}
