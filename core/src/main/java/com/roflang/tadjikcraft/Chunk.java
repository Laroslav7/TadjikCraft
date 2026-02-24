package com.roflang.tadjikcraft;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class Chunk {

    static final int SIZE = 16;

    int chunkX;
    int chunkZ;

    Mesh mesh;
    int vertexCount;

    public Chunk(int chunkX, int chunkZ){
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        buildMesh();
    }

    private void buildMesh(){

        float[] vertices = new float[SIZE*SIZE*6*6*3];
        int idx = 0;

        for(int x=0;x<SIZE;x++){
            for(int z=0;z<SIZE;z++){

                float worldX = chunkX*SIZE + x;
                float worldZ = chunkZ*SIZE + z;
                float y = 0;

                idx = addCube(vertices, idx, worldX, y, worldZ);
            }
        }

        mesh = new Mesh(true,
            idx/3,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position,3,"a_position")
        );

        mesh.setVertices(vertices,0,idx);
        vertexCount = idx/3;
    }

    private int addCube(float[] v,int idx,float x,float y,float z){

        float s=1f;

        // только верхняя грань (для примера)
        float[] face={
            x,y+s,z,
            x+s,y+s,z,
            x+s,y+s,z+s,

            x,y+s,z,
            x+s,y+s,z+s,
            x,y+s,z+s
        };

        System.arraycopy(face,0,v,idx,face.length);
        return idx+face.length;
    }

    public void render(ShaderProgram shader){
        mesh.render(shader,GL20.GL_TRIANGLES,0,vertexCount);
    }

    public void dispose(){
        mesh.dispose();
    }
}
