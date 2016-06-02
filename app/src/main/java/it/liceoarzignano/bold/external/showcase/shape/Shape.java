/**
 * Copyright 2015 Dean Wild
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.liceoarzignano.bold.external.showcase.shape;

import android.graphics.Canvas;
import android.graphics.Paint;

import it.liceoarzignano.bold.external.showcase.target.Target;

/**
 * Specifies a shape of the target (e.g circle, rectangle).
 * Implementations of this interface will be responsible to draw the shape
 * at specified center point (x, y).
 */
public interface Shape {

    /**
     * Draw shape on the canvas with the center at (x, y) using Paint object provided.
     */
    void draw(Canvas canvas, Paint paint, int x, int y, int padding);

    /**
     * Get height of the shape.
     */
    int getHeight();

    /**
     * Update shape bounds if necessary
     */
    void updateTarget(Target target);

}
