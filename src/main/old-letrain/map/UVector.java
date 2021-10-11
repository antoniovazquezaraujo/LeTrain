package letrain.map;

public class UVector {
 
  public float x;

  protected float[] array;
 
  public UVector() {
  } 
 

  public UVector(float x) {
    this.x = x; 
  }


    public void set(UVector v) {
    x = v.x; 
  }
 
 
  public void set(float source) {
      x = source;
  }
 

  public UVector get() {
    return new UVector(x);
  } 
 
 
  public float mag() {
    return (float) Math.sqrt(x*x);
  } 
 
  public void add(UVector v) {
    x += v.x; 
  }
 
 
  public void add(float x) {
    this.x += x; 
  }
 
 
  static public UVector add(UVector v1, UVector v2) {
    return add(v1, v2, null); 
  } 
 
 
  static public UVector add(UVector v1, UVector v2, UVector target) {
    if (target == null) { 
      target = new UVector(v1.x + v2.x);
    } else { 
      target.set(v1.x + v2.x);
    } 
    return target; 
  } 
 
 
  public void sub(UVector v) {
    x -= v.x; 
  }
 
 
  public void sub(float x, float y, float z) { 
    this.x -= x; 
  }

  static public UVector sub(UVector v1, UVector v2) {
    return sub(v1, v2, null); 
  } 
 
 
  static public UVector sub(UVector v1, UVector v2, UVector target) {
    if (target == null) { 
      target = new UVector(v1.x - v2.x);
    } else { 
      target.set(v1.x - v2.x);
    } 
    return target; 
  } 
 
 
  public void mult(float n) {
    x *= n; 
  }
 
 
  static public UVector mult(UVector v, float n) {
    return mult(v, n, null); 
  } 
 
 
  static public UVector mult(UVector v, float n, UVector target) {
    if (target == null) { 
      target = new UVector(v.x*n);
    } else { 
      target.set(v.x*n);
    } 
    return target; 
  } 
 
  public void mult(UVector v) {
    x *= v.x; 
  }
 
 
  static public UVector mult(UVector v1, UVector v2) {
    return mult(v1, v2, null); 
  } 
 
  static public UVector mult(UVector v1, UVector v2, UVector target) {
    if (target == null) { 
      target = new UVector(v1.x*v2.x );
    } else { 
      target.set(v1.x*v2.x );
    } 
    return target; 
  } 
 
 
  /**
   * Divide this vector by a scalar 
   * @param n the value to divide by 
   */ 
  public void div(float n) { 
    x /= n; 

  } 
 
 
  /**
   * Divide a vector by a scalar and return the result in a new vector. 
   * @param v a vector 
   * @param n scalar 
   * @return a new vector that is v1 / n 
   */ 
  static public UVector div(UVector v, float n) {
    return div(v, n, null); 
  } 
 
 
  static public UVector div(UVector v, float n, UVector target) {
    if (target == null) { 
      target = new UVector(v.x/n );
    } else { 
      target.set(v.x/n );
    } 
    return target; 
  } 
 
 
  /**
   * Divide each element of one vector by the elements of another vector. 
   */ 
  public void div(UVector v) {
    x /= v.x; 

  } 
 
 
  /**
   * Multiply each element of one vector by the individual elements of another 
   * vector, and return the result as a new UVector.
   */ 
  static public UVector div(UVector v1, UVector v2) {
    return div(v1, v2, null); 
  } 
 
 
  /**
   * Divide each element of one vector by the individual elements of another 
   * vector, and write the result into a target vector. 
   * @param v1 the first vector 
   * @param v2 the second vector 
   * @param target UVector to store the result
   */ 
  static public UVector div(UVector v1, UVector v2, UVector target) {
    if (target == null) { 
      target = new UVector(v1.x/v2.x );
    } else { 
      target.set(v1.x/v2.x );
    } 
    return target; 
  } 
 
 
  /**
   * Calculate the Euclidean distance between two points (considering a point as a vector object) 
   * @param v another vector 
   * @return the Euclidean distance between 
   */ 
  public float dist(UVector v) {
    float dx = x - v.x; 

    return (float) Math.sqrt(dx*dx  );
  } 
 
 
  /**
   * Calculate the Euclidean distance between two points (considering a point as a vector object) 
   * @param v1 a vector 
   * @param v2 another vector 
   * @return the Euclidean distance between v1 and v2 
   */ 
  static public float dist(UVector v1, UVector v2) {
    float dx = v1.x - v2.x; 

    return (float) Math.sqrt(dx*dx );
  } 
 
 
  /**
   * Calculate the dot product with another vector 
   * @return the dot product 
   */ 
  public float dot(UVector v) {
    return this.x*v.x  ;
  } 
 
 
  public float dot(float x ) {
    return this.x*x  ;
  } 
   
   
  static public float dot(UVector v1, UVector v2) {
      return v1.x*v2.x  ;
  } 
 
 
  /**
   * Return a vector composed of the cross product between this and another. 
   */ 
  public UVector cross(UVector v) {
    return cross(v, null); 
  } 
 
 
  /**
   * Perform cross product between this and another vector, and store the 
   * result in 'target'. If target is null, a new vector is created. 
   */ 
  public UVector cross(UVector v, UVector target) {
    float crossX = 0;

    if (target == null) { 
      target = new UVector(crossX);
    } else { 
      target.set(crossX);
    } 
    return target; 
  } 
 
 
  static public UVector cross(UVector v1, UVector v2, UVector target) {
    float crossX = 0;

 
    if (target == null) { 
      target = new UVector(crossX);
    } else { 
      target.set(crossX);
    } 
    return target; 
  } 
 
 
  /**
   * Normalize the vector to length 1 (make it a unit vector) 
   */ 
  public void normalize() { 
    float m = mag(); 
    if (m != 0 && m != 1) { 
      div(m); 
    } 
  } 
 
 
  /**
   * Normalize this vector, storing the result in another vector. 
   * @param target Set to null to create a new vector 
   * @return a new vector (if target was null), or target 
   */ 
  public UVector normalize(UVector target) {
    if (target == null) { 
      target = new UVector();
    } 
    float m = mag(); 
    if (m > 0) { 
      target.set(x/m);
    } else { 
      target.set(x);
    } 
    return target; 
  } 
 
 
  /**
   * Limit the magnitude of this vector 
   * @param max the maximum length to limit this vector 
   */ 
  public void limit(float max) { 
    if (mag() > max) { 
      normalize(); 
      mult(max); 
    } 
  } 
 
 
  /**
   * Calculate the angle of rotation for this vector (only 2D vectors) 
   * @return the angle of rotation 
   */ 
  public float heading2D() { 
    return 0;
  } 
 
 
  /**
   * Calculate the angle between two vectors, using the dot product 
   * @param v1 a vector 
   * @param v2 another vector 
   * @return the angle between the vectors 
   */ 
  static public float angleBetween(UVector v1, UVector v2) {
    return 0;
  } 
 
 
  public String toString() { 
    return "[ " + x + "]";
  } 
 
 
  /**
   * Return a representation of this vector as a float array. This is only for 
   * temporary use. If used in any other fashion, the contents should be copied 
   * by using the get() command to copy into your own array. 
   */ 
  public float[] array() { 
    if (array == null) { 
      array = new float[1];
    } 
    array[0] = x; 
    return array;
  } 
}