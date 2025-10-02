package net.kronoz.odyssey.systems.cinematics.api;


public enum Easing {
    LINEAR,
    IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
    IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
    IN_QUART, OUT_QUART, IN_OUT_QUART,
    IN_QUINT, OUT_QUINT, IN_OUT_QUINT,
    IN_SINE, OUT_SINE, IN_OUT_SINE,
    IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
    IN_CIRC, OUT_CIRC, IN_OUT_CIRC,
    IN_BACK, OUT_BACK, IN_OUT_BACK;

    public double apply(double t) {
        return switch (this) {
            case LINEAR -> t;
            case IN_QUAD -> t*t;
            case OUT_QUAD -> 1 - (1 - t)*(1 - t);
            case IN_OUT_QUAD -> t < 0.5 ? 2*t*t : 1 - Math.pow(-2*t + 2, 2)/2;
            case IN_CUBIC -> t*t*t;
            case OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case IN_OUT_CUBIC -> t<0.5 ? 4*t*t*t : 1 - Math.pow(-2*t+2,3)/2;
            case IN_QUART -> Math.pow(t,4);
            case OUT_QUART -> 1 - Math.pow(1 - t,4);
            case IN_OUT_QUART -> t<0.5?8*t*t*t*t:1-Math.pow(-2*t+2,4)/2;
            case IN_QUINT -> Math.pow(t,5);
            case OUT_QUINT -> 1 - Math.pow(1 - t,5);
            case IN_OUT_QUINT -> t<0.5?16*t*t*t*t*t:1-Math.pow(-2*t+2,5)/2;
            case IN_SINE -> 1 - Math.cos((t*Math.PI)/2);
            case OUT_SINE -> Math.sin((t*Math.PI)/2);
            case IN_OUT_SINE -> -(Math.cos(Math.PI*t)-1)/2;
            case IN_EXPO -> t==0?0:Math.pow(2,10*t-10);
            case OUT_EXPO -> t==1?1:1-Math.pow(2,-10*t);
            case IN_OUT_EXPO -> t==0?0:t==1?1:(t<0.5?Math.pow(2,20*t-10)/2:(2- Math.pow(2,-20*t+10))/2);
            case IN_CIRC -> 1 - Math.sqrt(1 - t*t);
            case OUT_CIRC -> Math.sqrt(1 - Math.pow(t-1,2));
            case IN_OUT_CIRC -> t<0.5?(1-Math.sqrt(1-4*t*t))/2:(Math.sqrt(1-Math.pow(-2*t+2,2))+1)/2;
            case IN_BACK -> 2.70158*t*t*t - 1.70158*t*t;
            case OUT_BACK -> 1 + 2.70158*Math.pow(t-1,3) + 1.70158*Math.pow(t-1,2);
            case IN_OUT_BACK -> {
                double c1=1.70158, c2=c1*1.525;
                yield t<0.5? (Math.pow(2*t,2)*((c2+1)*2*t - c2))/2
                            : (Math.pow(2*t-2,2)*((c2+1)*(t*2-2)+c2)+2)/2;
            }
        };
    }
}
