public class Main {

    public static void main(String[] args) {

        MiniRedis redis = new MiniRedis();

        redis.set("name", "Gaurang");

        System.out.println(redis.get("name"));
        // Gaurang

        System.out.println(redis.exists("name"));
        // true

        redis.delete("name");

        System.out.println(redis.get("name"));
        // null
    }
}