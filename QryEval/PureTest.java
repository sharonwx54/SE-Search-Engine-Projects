public class PureTest {
    public void testtest(String s){
        if (s.equals("A"))
                System.out.println("A");
        else if (s.equals("B") || s.equals("C") || s.equals("D")) {
            if (s.equals("B")){
                System.out.println("B");
            }
        }
        else
            System.out.println("Else");
    }

}
